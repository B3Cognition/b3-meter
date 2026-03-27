package com.jmeternext.worker.node;

import com.google.protobuf.Timestamp;
import com.jmeternext.engine.adapter.EngineServiceImpl;
import com.jmeternext.engine.adapter.InMemorySampleStreamBroker;
import com.jmeternext.engine.adapter.NoOpUIBridge;
import com.jmeternext.engine.adapter.http.Hc5HttpClientFactory;
import com.jmeternext.engine.service.SampleBucket;
import com.jmeternext.engine.service.SampleBucketConsumer;
import com.jmeternext.engine.service.TestRunHandle;
import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.worker.proto.ConfigureAck;
import com.jmeternext.worker.proto.HealthRequest;
import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.SampleResultBatch;
import com.jmeternext.worker.proto.StartAck;
import com.jmeternext.worker.proto.StartMessage;
import com.jmeternext.worker.proto.StopAck;
import com.jmeternext.worker.proto.StopMessage;
import com.jmeternext.worker.proto.TestPlanMessage;
import com.jmeternext.worker.proto.WorkerServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * gRPC service implementation for the worker node.
 *
 * <p>Wires the gRPC contract to the real engine via {@link EngineServiceImpl}:
 * <ol>
 *   <li>{@link #configure} — stores the incoming JMX content and virtual-user count.</li>
 *   <li>{@link #start} — creates an {@link EngineServiceImpl}, calls {@code startRun()}
 *       with the stored JMX, and subscribes to the broker.</li>
 *   <li>{@link #streamResults} — forwards real {@link SampleBucket} events from the
 *       broker to gRPC observers, converting them to {@link SampleResultBatch} protos.</li>
 *   <li>{@link #stop} / {@link #stopNow} — delegates to the engine's stop methods.</li>
 *   <li>{@link #getHealth} — returns the actual worker state.</li>
 * </ol>
 *
 * <p>Thread-safety: all state is guarded by {@link AtomicReference} or
 * {@link ConcurrentHashMap}; no external synchronisation is required by callers.
 */
public class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {

    private static final Logger LOG = Logger.getLogger(WorkerServiceImpl.class.getName());

    // -------------------------------------------------------------------------
    // Worker-level state (shared across all RPCs)
    // -------------------------------------------------------------------------

    /** Current lifecycle state of this worker. */
    private final AtomicReference<WorkerState> state = new AtomicReference<>(WorkerState.IDLE);

    /** Epoch millis when this worker process started. */
    private final long startedAtMs = System.currentTimeMillis();

    /** Stored JMX content from the last configure() call. */
    private volatile String jmxContent;

    /** Stored virtual-user count from the last configure() call. */
    private volatile int virtualUsers;

    /** Stored duration in seconds from the last configure() call. */
    private volatile long durationSeconds;

    /** Stored run ID from the last configure() call. */
    private volatile String configuredRunId;

    /** Stream broker used to fan-out sample buckets to StreamResults observers. */
    private final InMemorySampleStreamBroker broker = new InMemorySampleStreamBroker();

    /** The HTTP client factory shared across runs. Created lazily on first start. */
    private volatile HttpClientFactory httpClientFactory;

    /** The engine service for the active run. */
    private volatile EngineServiceImpl engine;

    /** Active run handle (non-null while RUNNING or STOPPING). */
    private volatile TestRunHandle activeRunHandle;

    /** The engine's internal run ID (differs from configuredRunId). */
    private volatile String engineRunId;

    /**
     * Registered StreamResults observers keyed by runId.
     */
    private final ConcurrentHashMap<String, StreamObserver<SampleResultBatch>> resultObservers =
            new ConcurrentHashMap<>();

    /**
     * Broker consumers registered per runId, so we can unsubscribe on completion.
     */
    private final ConcurrentHashMap<String, SampleBucketConsumer> brokerConsumers =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Configure RPC
    // -------------------------------------------------------------------------

    /**
     * Accepts and validates a test plan from the controller.
     *
     * <p>Stores the JMX content, virtual-user count, and duration from the
     * {@link TestPlanMessage}. The worker must be IDLE before a plan can be accepted.
     */
    @Override
    public void configure(TestPlanMessage request, StreamObserver<ConfigureAck> responseObserver) {
        WorkerState current = state.get();
        if (current != WorkerState.IDLE) {
            LOG.log(Level.WARNING, "Configure rejected -- worker is in state {0}", current);
            responseObserver.onNext(ConfigureAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("Worker is not idle; current state: " + current)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getRunId() == null || request.getRunId().isBlank()) {
            responseObserver.onNext(ConfigureAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("run_id must not be blank")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        // Store configuration from proto message
        this.configuredRunId = request.getRunId();
        this.jmxContent = request.getPlanContent().toString(StandardCharsets.UTF_8);
        this.virtualUsers = Math.max(1, request.getVirtualUsers());
        this.durationSeconds = request.getDurationSeconds();

        state.set(WorkerState.CONFIGURED);
        LOG.log(Level.INFO, "Configured run {0} ({1} VUs, {2}s)",
                new Object[]{request.getRunId(), this.virtualUsers, this.durationSeconds});

        responseObserver.onNext(ConfigureAck.newBuilder()
                .setAccepted(true)
                .setMessage("Plan accepted for run " + request.getRunId())
                .build());
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // Start RPC
    // -------------------------------------------------------------------------

    /**
     * Starts execution of the previously configured test plan.
     *
     * <p>Creates an {@link EngineServiceImpl} backed by an {@link Hc5HttpClientFactory},
     * calls {@code startRun()} with the stored JMX content as {@code treeData}, and
     * subscribes to the broker for result forwarding. Transitions CONFIGURED to RUNNING.
     */
    @Override
    public void start(StartMessage request, StreamObserver<StartAck> responseObserver) {
        WorkerState current = state.get();
        if (current != WorkerState.CONFIGURED) {
            LOG.log(Level.WARNING, "Start rejected -- worker is in state {0}", current);
            responseObserver.onNext(StartAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("Worker is not in CONFIGURED state; current: " + current)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        String runId = configuredRunId;
        if (runId == null || !runId.equals(request.getRunId())) {
            responseObserver.onNext(StartAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("run_id mismatch -- configure first")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            // Create the HTTP client factory (lazily, reused across runs)
            if (httpClientFactory == null) {
                httpClientFactory = new Hc5HttpClientFactory();
            }

            // Create the engine service wired to our broker and HTTP client
            engine = new EngineServiceImpl(broker, httpClientFactory, NoOpUIBridge.INSTANCE);

            // Build treeData with JMX content for the NodeInterpreter pipeline
            Map<String, Object> treeData = new HashMap<>();
            treeData.put("jmxContent", jmxContent);

            // Build overrides with virtual-user count and duration
            Properties overrides = new Properties();
            overrides.setProperty(EngineServiceImpl.PROP_THREADS, String.valueOf(virtualUsers));
            overrides.setProperty(EngineServiceImpl.PROP_DURATION, String.valueOf(durationSeconds));

            // Start the run -- returns immediately with a handle; execution is on a background thread
            activeRunHandle = engine.startRun(runId, treeData, overrides);
            engineRunId = activeRunHandle.runId();

            state.set(WorkerState.RUNNING);
            LOG.log(Level.INFO, "Started run {0} with {1} VUs via real engine",
                    new Object[]{runId, virtualUsers});

            // Watch for run completion to log results; state transition to IDLE
            // happens via stop/stopNow RPCs or when the completion is observed.
            activeRunHandle.completion().whenComplete((result, ex) -> {
                if (ex != null) {
                    LOG.log(Level.SEVERE, "Run " + runId + " completed exceptionally", ex);
                    state.set(WorkerState.ERROR);
                    finishRun(runId);
                } else {
                    LOG.log(Level.INFO, "Run {0} completed: status={1}, samples={2}, errors={3}",
                            new Object[]{runId, result.finalStatus(), result.totalSamples(), result.errorCount()});
                    // Mark the run as completed but keep state for stop/stopNow to clean up.
                    // If the state is STOPPING (explicit stop was requested), transition to IDLE now.
                    if (state.get() == WorkerState.STOPPING) {
                        finishRun(runId);
                    }
                }
            });

            responseObserver.onNext(StartAck.newBuilder()
                    .setAccepted(true)
                    .setMessage("Run " + runId + " started")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to start run " + runId, ex);
            state.set(WorkerState.ERROR);
            responseObserver.onNext(StartAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("Failed to start: " + ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // -------------------------------------------------------------------------
    // Stop / StopNow RPCs
    // -------------------------------------------------------------------------

    /**
     * Gracefully stops the running test (allows in-flight samples to complete).
     *
     * <p>Delegates to {@link EngineServiceImpl#stopRun(String)} which sets
     * {@code context.running = false} so virtual users finish their current iteration.
     */
    @Override
    public void stop(StopMessage request, StreamObserver<StopAck> responseObserver) {
        handleStop(request.getRunId(), false, responseObserver);
    }

    /**
     * Immediately aborts the running test without waiting for in-flight samples.
     *
     * <p>Delegates to {@link EngineServiceImpl#stopRunNow(String)} which interrupts
     * the run's thread immediately.
     */
    @Override
    public void stopNow(StopMessage request, StreamObserver<StopAck> responseObserver) {
        handleStop(request.getRunId(), true, responseObserver);
    }

    private void handleStop(String runId, boolean immediate, StreamObserver<StopAck> responseObserver) {
        WorkerState current = state.get();
        if (current != WorkerState.RUNNING && current != WorkerState.STOPPING) {
            responseObserver.onNext(StopAck.newBuilder()
                    .setAccepted(false)
                    .setMessage("No active run; current state: " + current)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        EngineServiceImpl eng = engine;

        // Use the engine's internal runId for stop calls (differs from configured runId)
        String internalRunId = engineRunId != null ? engineRunId : runId;

        if (eng != null) {
            if (immediate) {
                eng.stopRunNow(internalRunId);
                LOG.log(Level.INFO, "Run {0} stop-now requested via engine", runId);
            } else {
                state.set(WorkerState.STOPPING);
                eng.stopRun(internalRunId);
                LOG.log(Level.INFO, "Run {0} graceful stop requested via engine", runId);
            }
        } else {
            LOG.log(Level.INFO, "Run {0} already completed; cleaning up", runId);
        }

        // If the run has already completed (engine handle done), transition to IDLE immediately
        TestRunHandle handle = activeRunHandle;
        if (handle == null || handle.completion().isDone()) {
            finishRun(runId);
        } else if (!immediate) {
            state.set(WorkerState.STOPPING);
        }

        responseObserver.onNext(StopAck.newBuilder()
                .setAccepted(true)
                .setMessage("Stop requested for run " + runId)
                .build());
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // StreamResults RPC
    // -------------------------------------------------------------------------

    /**
     * Server-streaming RPC that delivers {@link SampleResultBatch} messages to the caller.
     *
     * <p>Subscribes a {@link SampleBucketConsumer} to the broker that converts each
     * real {@link SampleBucket} from the engine into a {@link SampleResultBatch} proto
     * and forwards it to the gRPC observer.
     */
    @Override
    public void streamResults(StopMessage request, StreamObserver<SampleResultBatch> responseObserver) {
        String runId = request.getRunId();
        resultObservers.put(runId, responseObserver);

        // Subscribe a consumer that translates SampleBucket -> SampleResultBatch proto
        SampleBucketConsumer consumer = bucket -> {
            StreamObserver<SampleResultBatch> obs = resultObservers.get(runId);
            if (obs == null) return;
            try {
                Instant ts = bucket.timestamp();
                SampleResultBatch batch = SampleResultBatch.newBuilder()
                        .setTimestamp(Timestamp.newBuilder()
                                .setSeconds(ts.getEpochSecond())
                                .setNanos(ts.getNano())
                                .build())
                        .setSamplerLabel(bucket.samplerLabel())
                        .setSampleCount(bucket.sampleCount())
                        .setErrorCount(bucket.errorCount())
                        .setAvgResponseTime(bucket.avgResponseTime())
                        .putPercentiles("p90", bucket.percentile90())
                        .putPercentiles("p95", bucket.percentile95())
                        .putPercentiles("p99", bucket.percentile99())
                        .build();
                obs.onNext(batch);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Error forwarding batch to StreamResults observer", ex);
                resultObservers.remove(runId);
            }
        };

        brokerConsumers.put(runId, consumer);
        broker.subscribe(runId, consumer);
        LOG.log(Level.INFO, "StreamResults observer registered for run {0}", runId);
    }

    // -------------------------------------------------------------------------
    // GetHealth RPC
    // -------------------------------------------------------------------------

    /**
     * Returns the current worker state and uptime without side-effects.
     *
     * <p>When a run is active, queries the engine for the actual run status and
     * reflects it in the response.
     */
    @Override
    public void getHealth(HealthRequest request, StreamObserver<HealthStatus> responseObserver) {
        WorkerState current = state.get();
        String activeRunId = configuredRunId != null ? configuredRunId : "";

        HealthStatus status = HealthStatus.newBuilder()
                .setState(current.toProto())
                .setMessage("Worker is " + current.name().toLowerCase())
                .setActiveRunId(activeRunId)
                .setTimestampMs(System.currentTimeMillis())
                .build();

        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Completes the run: transitions worker to IDLE, completes all open StreamResults
     * observers, unsubscribes broker consumers, and cleans up engine references.
     *
     * @param runId the run being finished
     */
    private void finishRun(String runId) {
        activeRunHandle = null;
        engine = null;
        configuredRunId = null;
        engineRunId = null;
        jmxContent = null;

        // Only go to IDLE if not already in ERROR
        state.compareAndSet(WorkerState.RUNNING, WorkerState.IDLE);
        state.compareAndSet(WorkerState.STOPPING, WorkerState.IDLE);

        // Unsubscribe broker consumer
        SampleBucketConsumer consumer = brokerConsumers.remove(runId);
        if (consumer != null) {
            broker.unsubscribe(runId, consumer);
        }

        // Complete all open StreamResults observers for this run
        StreamObserver<SampleResultBatch> obs = resultObservers.remove(runId);
        if (obs != null) {
            try {
                obs.onCompleted();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Error completing StreamResults observer for run " + runId, ex);
            }
        }
        LOG.log(Level.INFO, "Run {0} finished; worker back to IDLE", runId);
    }

    /**
     * Returns the current worker state (visible for testing).
     *
     * @return the current {@link WorkerState}; never {@code null}
     */
    WorkerState currentState() {
        return state.get();
    }

    /**
     * Shuts down internal resources. Should be called when the gRPC server is stopped.
     */
    public void shutdown() {
        HttpClientFactory client = httpClientFactory;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Error closing HttpClientFactory", ex);
            }
        }
    }
}
