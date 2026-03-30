/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.distributed.controller;

import com.b3meter.worker.proto.ConfigureAck;
import com.b3meter.worker.proto.HealthRequest;
import com.b3meter.worker.proto.HealthStatus;
import com.b3meter.worker.proto.SampleResultBatch;
import com.b3meter.worker.proto.StartAck;
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopAck;
import com.b3meter.worker.proto.StopMessage;
import com.b3meter.worker.proto.TestPlanMessage;
import com.b3meter.worker.proto.WorkerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side facade for a single remote worker node, communicating via gRPC.
 *
 * <p>Wraps a {@link ManagedChannel} and provides typed methods for each RPC in
 * {@code WorkerService}. All non-streaming calls are sent with a 30-second deadline.
 *
 * <p>Circuit-breaker: the client tracks consecutive RPC failures. After 3 consecutive
 * failures the circuit is tripped and {@link #isCircuitOpen()} returns {@code true}.
 * The circuit is reset on the next successful call.
 *
 * <p>Thread-safety: all public methods are thread-safe. The channel and stubs are
 * created once at construction and are internally thread-safe per the gRPC contract.
 */
public final class WorkerClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WorkerClient.class.getName());

    /** Deadline applied to all non-streaming RPCs. */
    static final long DEADLINE_SECONDS = 30L;

    /** Number of consecutive failures that trip the circuit breaker. */
    static final int CIRCUIT_TRIP_THRESHOLD = 3;

    private final String workerId;
    private final ManagedChannel channel;
    private final WorkerServiceGrpc.WorkerServiceBlockingStub blockingStub;
    private final WorkerServiceGrpc.WorkerServiceStub asyncStub;

    /** Count of consecutive RPC failures since the last successful call. */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** {@code true} when the circuit breaker has tripped. */
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    /**
     * Creates a client that connects to the worker at {@code host:port}.
     *
     * @param workerId unique identifier for this worker (used in log messages)
     * @param host     hostname or IP of the worker node
     * @param port     gRPC port of the worker node
     */
    public WorkerClient(String workerId, String host, int port) {
        this(workerId,
             ManagedChannelBuilder.forAddress(host, port)
                     .usePlaintext()
                     .build());
    }

    /**
     * Creates a client using the provided channel (useful for in-process testing).
     *
     * @param workerId unique identifier for this worker
     * @param channel  pre-built channel (caller retains ownership for shutdown if not
     *                 using {@link #close()})
     */
    public WorkerClient(String workerId, ManagedChannel channel) {
        this.workerId = workerId;
        this.channel = channel;
        this.blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
        this.asyncStub = WorkerServiceGrpc.newStub(channel);
    }

    // -------------------------------------------------------------------------
    // RPC wrappers
    // -------------------------------------------------------------------------

    /**
     * Sends the test plan to the worker node for validation.
     *
     * @param request the plan message
     * @return the worker's ack; {@link ConfigureAck#getAccepted()} is {@code false}
     *         on rejection or circuit-open
     */
    public ConfigureAck configure(TestPlanMessage request) {
        try {
            ConfigureAck ack = blockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .configure(request);
            recordSuccess();
            return ack;
        } catch (Exception ex) {
            return handleFailure("configure", ex,
                    ConfigureAck.newBuilder().setAccepted(false)
                            .setMessage("RPC failed: " + ex.getMessage()).build());
        }
    }

    /**
     * Instructs the worker to start the previously configured run.
     *
     * @param request the start message (includes optional coordinated start timestamp)
     * @return the worker's ack
     */
    public StartAck start(StartMessage request) {
        try {
            StartAck ack = blockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .start(request);
            recordSuccess();
            return ack;
        } catch (Exception ex) {
            return handleFailure("start", ex,
                    StartAck.newBuilder().setAccepted(false)
                            .setMessage("RPC failed: " + ex.getMessage()).build());
        }
    }

    /**
     * Gracefully stops the active run on the worker.
     *
     * @param runId the run to stop
     * @return the worker's ack
     */
    public StopAck stop(String runId) {
        try {
            StopAck ack = blockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .stop(StopMessage.newBuilder().setRunId(runId).build());
            recordSuccess();
            return ack;
        } catch (Exception ex) {
            return handleFailure("stop", ex,
                    StopAck.newBuilder().setAccepted(false)
                            .setMessage("RPC failed: " + ex.getMessage()).build());
        }
    }

    /**
     * Immediately aborts the active run on the worker.
     *
     * @param runId the run to abort
     * @return the worker's ack
     */
    public StopAck stopNow(String runId) {
        try {
            StopAck ack = blockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .stopNow(StopMessage.newBuilder().setRunId(runId).build());
            recordSuccess();
            return ack;
        } catch (Exception ex) {
            return handleFailure("stopNow", ex,
                    StopAck.newBuilder().setAccepted(false)
                            .setMessage("RPC failed: " + ex.getMessage()).build());
        }
    }

    /**
     * Probes the worker's current health and lifecycle state.
     *
     * @param runId optional run ID to scope the health report; may be empty
     * @return the health status; on failure returns a status with UNSPECIFIED state
     */
    public HealthStatus getHealth(String runId) {
        try {
            HealthStatus status = blockingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getHealth(HealthRequest.newBuilder().setRunId(runId).build());
            recordSuccess();
            return status;
        } catch (Exception ex) {
            return handleFailure("getHealth", ex,
                    HealthStatus.newBuilder()
                            .setState(com.b3meter.worker.proto.WorkerState.WORKER_STATE_UNSPECIFIED)
                            .setMessage("RPC failed: " + ex.getMessage())
                            .setTimestampMs(System.currentTimeMillis())
                            .build());
        }
    }

    /**
     * Opens a server-streaming RPC to receive result batches from the worker.
     *
     * <p>This call is non-blocking: it registers the {@code batchConsumer} callback
     * and opens the stream asynchronously. The consumer is called from the gRPC
     * thread pool; implementations must be thread-safe.
     *
     * @param runId         the run whose results should be streamed
     * @param batchConsumer callback invoked for each received {@link SampleResultBatch}
     * @param onComplete    callback invoked when the stream ends normally
     * @param onError       callback invoked if the stream ends with an error
     */
    public void streamResults(
            String runId,
            Consumer<SampleResultBatch> batchConsumer,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        asyncStub.streamResults(
                StopMessage.newBuilder().setRunId(runId).build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(SampleResultBatch batch) {
                        try {
                            batchConsumer.accept(batch);
                        } catch (Exception ex) {
                            LOG.log(Level.WARNING,
                                    "batchConsumer threw for worker={0}, runId={1}",
                                    new Object[]{workerId, runId});
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LOG.log(Level.WARNING,
                                "StreamResults error for worker={0}, runId={1}: {2}",
                                new Object[]{workerId, runId, t.getMessage()});
                        recordFailure("streamResults");
                        onError.accept(t);
                    }

                    @Override
                    public void onCompleted() {
                        LOG.log(Level.INFO,
                                "StreamResults completed for worker={0}, runId={1}",
                                new Object[]{workerId, runId});
                        onComplete.run();
                    }
                });
    }

    /**
     * Opens a blocking iterator over result batches (useful for tests).
     *
     * @param runId the run to stream
     * @return blocking iterator; caller must consume or abandon it before the run ends
     */
    public Iterator<SampleResultBatch> streamResultsBlocking(String runId) {
        return blockingStub.streamResults(
                StopMessage.newBuilder().setRunId(runId).build());
    }

    // -------------------------------------------------------------------------
    // Circuit breaker
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the circuit breaker has tripped (3+ consecutive failures).
     * When open, the caller should stop routing work to this worker.
     *
     * @return {@code true} when the circuit is open
     */
    public boolean isCircuitOpen() {
        return circuitOpen.get();
    }

    /**
     * Resets the circuit breaker manually (e.g. after an operator confirms the worker
     * is healthy again).
     */
    public void resetCircuit() {
        consecutiveFailures.set(0);
        circuitOpen.set(false);
        LOG.log(Level.INFO, "Circuit reset for worker={0}", workerId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the logical worker identifier supplied at construction.
     *
     * @return worker ID; never {@code null}
     */
    public String getWorkerId() {
        return workerId;
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------

    /**
     * Shuts down the underlying gRPC channel, waiting up to 5 seconds for in-flight
     * RPCs to complete before forcing closure.
     */
    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpen.set(false);
    }

    private void recordFailure(String rpcName) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_TRIP_THRESHOLD) {
            circuitOpen.compareAndSet(false, true);
            LOG.log(Level.WARNING,
                    "Circuit breaker tripped for worker={0} after {1} consecutive failures (last RPC: {2})",
                    new Object[]{workerId, failures, rpcName});
        }
    }

    private <T> T handleFailure(String rpcName, Exception ex, T fallback) {
        LOG.log(Level.WARNING,
                "RPC {0} failed for worker={1}: {2}",
                new Object[]{rpcName, workerId, ex.getMessage()});
        recordFailure(rpcName);
        return fallback;
    }
}
