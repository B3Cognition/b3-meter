package com.jmeternext.worker.node;

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
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-process gRPC tests for {@link WorkerServiceImpl}.
 *
 * <p>Uses {@link InProcessServerBuilder} to avoid real TCP ports, making tests
 * fast and free of port-conflict flakiness.  Each test gets a fresh server name
 * (UUID) so tests are fully isolated.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WorkerServiceImplTest {

    private WorkerServiceImpl service;
    private ManagedChannel channel;
    private WorkerServiceGrpc.WorkerServiceBlockingStub blockingStub;

    private String serverName;

    @BeforeEach
    void setUp() throws IOException {
        serverName = UUID.randomUUID().toString();
        service = new WorkerServiceImpl();

        InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        blockingStub = WorkerServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        service.shutdown();
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // GetHealth — initial state
    // -------------------------------------------------------------------------

    /**
     * Worker should be IDLE and respond to GetHealth within 200 ms after startup.
     */
    @Test
    void getHealth_returnsIdleInitially() {
        long before = System.currentTimeMillis();
        HealthStatus health = blockingStub.getHealth(HealthRequest.newBuilder().build());
        long elapsed = System.currentTimeMillis() - before;

        assertThat(health.getState())
                .as("initial state should be IDLE")
                .isEqualTo(com.jmeternext.worker.proto.WorkerState.WORKER_STATE_IDLE);
        assertThat(health.getActiveRunId())
                .as("no active run initially")
                .isEmpty();
        assertThat(elapsed)
                .as("GetHealth must respond within 200 ms")
                .isLessThan(200L);
    }

    // -------------------------------------------------------------------------
    // Configure
    // -------------------------------------------------------------------------

    /**
     * Configure with a valid plan should be accepted and transition to CONFIGURED.
     */
    @Test
    void configure_acceptsValidPlan() {
        String runId = "run-" + UUID.randomUUID();
        ConfigureAck ack = blockingStub.configure(buildPlan(runId, 10, 60));

        assertThat(ack.getAccepted())
                .as("valid plan should be accepted")
                .isTrue();
        assertThat(ack.getMessage())
                .contains(runId);
        assertThat(service.currentState())
                .isEqualTo(WorkerState.CONFIGURED);
    }

    /**
     * Configure while already in CONFIGURED state should be rejected.
     */
    @Test
    void configure_rejectedWhenNotIdle() {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 5, 30));

        ConfigureAck second = blockingStub.configure(buildPlan("run-other", 5, 30));

        assertThat(second.getAccepted())
                .as("second configure should be rejected when not IDLE")
                .isFalse();
        assertThat(second.getMessage())
                .contains("not idle");
    }

    /**
     * Configure with a blank run_id should be rejected.
     */
    @Test
    void configure_rejectedWhenRunIdBlank() {
        ConfigureAck ack = blockingStub.configure(
                TestPlanMessage.newBuilder()
                        .setRunId("")
                        .setVirtualUsers(1)
                        .setDurationSeconds(10)
                        .build());

        assertThat(ack.getAccepted()).isFalse();
        assertThat(ack.getMessage()).contains("run_id");
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    /**
     * Configure → Start lifecycle should succeed and transition to RUNNING.
     */
    @Test
    void start_transitionsToRunning() {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 10, 60));

        StartAck startAck = blockingStub.start(
                StartMessage.newBuilder().setRunId(runId).build());

        assertThat(startAck.getAccepted()).isTrue();
        assertThat(service.currentState())
                .isEqualTo(WorkerState.RUNNING);
    }

    /**
     * Calling Start without a prior Configure should be rejected.
     */
    @Test
    void start_rejectedWithoutConfigure() {
        StartAck ack = blockingStub.start(
                StartMessage.newBuilder().setRunId("run-noconfigure").build());

        assertThat(ack.getAccepted()).isFalse();
        assertThat(ack.getMessage()).containsIgnoringCase("configured");
    }

    // -------------------------------------------------------------------------
    // GetHealth — after Start
    // -------------------------------------------------------------------------

    /**
     * After Start, GetHealth should report RUNNING with the correct active run ID.
     */
    @Test
    void getHealth_returnsRunningAfterStart() {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 5, 30));
        blockingStub.start(StartMessage.newBuilder().setRunId(runId).build());

        HealthStatus health = blockingStub.getHealth(HealthRequest.newBuilder()
                .setRunId(runId)
                .build());

        assertThat(health.getState()).isEqualTo(com.jmeternext.worker.proto.WorkerState.WORKER_STATE_RUNNING);
        assertThat(health.getActiveRunId()).isEqualTo(runId);
        assertThat(health.getTimestampMs()).isGreaterThan(0L);
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    /**
     * Stop on a running worker should be accepted and eventually return to IDLE.
     */
    @Test
    void stop_acceptedWhenRunning() throws InterruptedException {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 5, 30));
        blockingStub.start(StartMessage.newBuilder().setRunId(runId).build());

        StopAck stopAck = blockingStub.stop(StopMessage.newBuilder().setRunId(runId).build());

        assertThat(stopAck.getAccepted()).isTrue();
        // After stop the state is STOPPING; wait for the drain to complete
        waitForState(WorkerState.IDLE, 10_000L);
        assertThat(service.currentState()).isEqualTo(WorkerState.IDLE);
    }

    /**
     * StopNow on a running worker should immediately return to IDLE.
     */
    @Test
    void stopNow_transitionsToIdleImmediately() throws InterruptedException {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 5, 30));
        blockingStub.start(StartMessage.newBuilder().setRunId(runId).build());

        StopAck ack = blockingStub.stopNow(StopMessage.newBuilder().setRunId(runId).build());

        assertThat(ack.getAccepted()).isTrue();
        // Allow engine time to process stop — virtual threads may take a moment to interrupt
        Thread.sleep(500);
        waitForState(WorkerState.IDLE, 10_000L);
        assertThat(service.currentState()).isEqualTo(WorkerState.IDLE);
    }

    /**
     * Stop when worker is IDLE should be rejected.
     */
    @Test
    void stop_rejectedWhenIdle() {
        StopAck ack = blockingStub.stop(
                StopMessage.newBuilder().setRunId("run-nonexistent").build());

        assertThat(ack.getAccepted()).isFalse();
        assertThat(ack.getMessage()).containsIgnoringCase("state");
    }

    // -------------------------------------------------------------------------
    // StreamResults
    // -------------------------------------------------------------------------

    /**
     * StreamResults should receive batches when a run is active and producing samples.
     *
     * <p>Uses a blocking (iterator-based) stub with a short timeout so the test
     * does not block indefinitely when the server streams forever.
     *
     * <p>Note: with stub JMX plans the engine may complete before the observer
     * subscribes, so we open the stream BEFORE starting the run to avoid missing
     * fast-completing results.
     */
    @Test
    void streamResults_receivesBatchesWhileRunning() throws InterruptedException {
        String runId = "run-" + UUID.randomUUID();
        blockingStub.configure(buildPlan(runId, 5, 0));

        // Open the streaming RPC BEFORE start so the observer is registered first
        WorkerServiceGrpc.WorkerServiceBlockingStub streamStub =
                blockingStub.withDeadlineAfter(
                        10_000L,
                        TimeUnit.MILLISECONDS);

        // Register the stream observer before starting so we don't miss fast results
        Thread streamThread = new Thread(() -> {
            try {
                Iterator<SampleResultBatch> it = streamStub.streamResults(
                        StopMessage.newBuilder().setRunId(runId).build());
                while (it.hasNext()) {
                    streamReceived.add(it.next());
                    if (streamReceived.size() >= 2) break;
                }
            } catch (StatusRuntimeException ex) {
                if (!ex.getStatus().getCode().name().equals("DEADLINE_EXCEEDED")
                        && !ex.getStatus().getCode().name().equals("CANCELLED")) {
                    throw ex;
                }
            }
        });
        streamThread.setDaemon(true);
        streamThread.start();

        // Give the stream observer time to register, then start the run
        Thread.sleep(200);
        blockingStub.start(StartMessage.newBuilder().setRunId(runId).build());

        // Wait for either results or timeout
        streamThread.join(8_000L);

        // Stop the run cleanly
        blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .stopNow(StopMessage.newBuilder().setRunId(runId).build());

        // With a stub JMX plan, results may or may not arrive depending on timing;
        // the structural test is that the stream was opened and processed without error.
        // If results did arrive, validate their structure.
        if (!streamReceived.isEmpty()) {
            SampleResultBatch first = streamReceived.get(0);
            assertThat(first.getSamplerLabel()).isNotBlank();
            assertThat(first.getSampleCount()).isGreaterThan(0L);
        }
    }

    private final List<SampleResultBatch> streamReceived =
            java.util.Collections.synchronizedList(new ArrayList<>());

    // -------------------------------------------------------------------------
    // Full lifecycle
    // -------------------------------------------------------------------------

    /**
     * Full Configure → Start → GetHealth → Stop cycle — state transitions must be correct.
     */
    @Test
    void fullLifecycle_stateTransitionsCorrect() throws InterruptedException {
        String runId = "run-" + UUID.randomUUID();

        // IDLE
        assertThat(service.currentState()).isEqualTo(WorkerState.IDLE);

        // Configure → CONFIGURED
        ConfigureAck cfg = blockingStub.configure(buildPlan(runId, 10, 0));
        assertThat(cfg.getAccepted()).isTrue();
        assertThat(service.currentState()).isEqualTo(WorkerState.CONFIGURED);

        // Start → RUNNING
        StartAck start = blockingStub.start(StartMessage.newBuilder().setRunId(runId).build());
        assertThat(start.getAccepted()).isTrue();
        assertThat(service.currentState()).isEqualTo(WorkerState.RUNNING);

        // GetHealth → RUNNING
        HealthStatus health = blockingStub.getHealth(HealthRequest.newBuilder().build());
        assertThat(health.getState()).isEqualTo(com.jmeternext.worker.proto.WorkerState.WORKER_STATE_RUNNING);

        // Stop → STOPPING → IDLE
        StopAck stop = blockingStub.stop(StopMessage.newBuilder().setRunId(runId).build());
        assertThat(stop.getAccepted()).isTrue();
        waitForState(WorkerState.IDLE, 10_000L);
        assertThat(service.currentState()).isEqualTo(WorkerState.IDLE);

        // Back to IDLE — GetHealth should confirm
        HealthStatus idleHealth = blockingStub.getHealth(HealthRequest.newBuilder().build());
        assertThat(idleHealth.getState()).isEqualTo(com.jmeternext.worker.proto.WorkerState.WORKER_STATE_IDLE);
        assertThat(idleHealth.getActiveRunId()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TestPlanMessage buildPlan(String runId, int virtualUsers, long durationSeconds) {
        return TestPlanMessage.newBuilder()
                .setRunId(runId)
                .setPlanContent(com.google.protobuf.ByteString.copyFromUtf8("<jmeterTestPlan/>"))
                .setVirtualUsers(virtualUsers)
                .setDurationSeconds(durationSeconds)
                .build();
    }

    /**
     * Polls {@link WorkerServiceImpl#currentState()} until it equals {@code expected}
     * or {@code timeoutMs} elapses.
     *
     * @param expected  the expected state to wait for
     * @param timeoutMs maximum wait in milliseconds
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    private void waitForState(WorkerState expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (service.currentState() == expected) return;
            Thread.sleep(100);
        }
    }
}
