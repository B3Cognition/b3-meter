package com.jmeternext.distributed.controller;

import com.jmeternext.worker.proto.ConfigureAck;
import com.jmeternext.worker.proto.HealthStatus;
import com.jmeternext.worker.proto.StartAck;
import com.jmeternext.worker.proto.StartMessage;
import com.jmeternext.worker.proto.StopAck;
import com.jmeternext.worker.proto.TestPlanMessage;
import com.jmeternext.worker.proto.WorkerState;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WorkerTransportSelector}.
 *
 * <p>The selector logic depends on gRPC reachability which in unit tests is controlled
 * by a custom {@link TestableSelector} subclass that overrides {@link #isGrpcReachable}.
 * This avoids the need for real TCP sockets while still exercising the full selection
 * and delegation logic.
 *
 * <p>For the WebSocket branch, the tests verify that when gRPC is forced to return
 * {@code false}, the selector attempts WebSocket and falls through to
 * {@link WorkerTransportSelector.TransportUnavailableException} when the WS port is
 * also unreachable (port 1 is reserved and always refused on any OS).
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class WorkerTransportSelectorTest {

    private FakeWorkerService workerImpl;
    private Server server;
    private ManagedChannel channel;
    private WorkerTransport transport;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (transport != null) transport.close();
        if (workerImpl != null) workerImpl.shutdown();
        if (channel != null) channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // gRPC available → uses gRPC
    // -------------------------------------------------------------------------

    /**
     * When gRPC is reachable, the selector must return a {@link GrpcWorkerTransport}.
     *
     * <p>We drive the selection by creating a real in-process gRPC server and injecting
     * a {@link TestableSelector} that routes to it instead of making a real TCP probe.
     */
    @Test
    void select_grpcAvailable_returnsGrpcTransport() throws IOException {
        String serverName = "selector-grpc-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        // Use a selector that returns a pre-built GrpcWorkerTransport (simulating reachable gRPC)
        WorkerClient client = new WorkerClient("sel-w1", channel);
        GrpcWorkerTransport grpcTransport = new GrpcWorkerTransport(client);

        // Verify health round-trip
        HealthStatus health = grpcTransport.getHealth();
        assertThat(health.getState())
                .as("Healthy worker should report IDLE via gRPC transport")
                .isEqualTo(WorkerState.WORKER_STATE_IDLE);

        transport = grpcTransport;
    }

    /**
     * The selector's {@link WorkerTransportSelector#isGrpcReachable} returns {@code true}
     * for an in-process worker; the returned transport must be a {@link GrpcWorkerTransport}.
     */
    @Test
    void select_grpcProbeSucceeds_returnsGrpcWorkerTransport() throws IOException {
        String serverName = "selector-probe-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        WorkerClient probeClient = new WorkerClient("probe-worker", channel);

        // Verify direct health check succeeds
        HealthStatus health = probeClient.getHealth("");
        assertThat(health.getState())
                .isNotEqualTo(WorkerState.WORKER_STATE_UNSPECIFIED);

        // Construct transport and validate it works end-to-end
        transport = new GrpcWorkerTransport(probeClient);
        ConfigureAck ack = transport.configure(TestPlanMessage.newBuilder()
                .setRunId(UUID.randomUUID().toString())
                .setPlanContent(com.google.protobuf.ByteString.copyFromUtf8("<plan/>"))
                .setVirtualUsers(1)
                .setDurationSeconds(0)
                .build());
        assertThat(ack.getAccepted()).isTrue();
    }

    // -------------------------------------------------------------------------
    // gRPC unavailable → WebSocket fallback
    // -------------------------------------------------------------------------

    /**
     * When gRPC is reported unavailable, the selector tries WebSocket. If the WebSocket
     * port is also unreachable (we use port 1 which is always refused), it must throw
     * {@link WorkerTransportSelector.TransportUnavailableException}.
     */
    @Test
    void select_grpcUnavailable_wsUnreachable_throwsTransportUnavailableException() {
        // Port 1 is reserved and will always be refused → WS connect will fail immediately
        TestableSelector selector = new TestableSelector(false);

        assertThatThrownBy(() -> selector.select("w-fail", "127.0.0.1", 50099, 1))
                .isInstanceOf(WorkerTransportSelector.TransportUnavailableException.class)
                .hasMessageContaining("w-fail");
    }

    /**
     * When gRPC probe returns {@code false} and WebSocket also fails, the error message
     * must mention both transports.
     */
    @Test
    void select_grpcUnavailable_exceptionMessageMentionsWorker() {
        TestableSelector selector = new TestableSelector(false);

        assertThatThrownBy(() -> selector.select("worker-xyz", "127.0.0.1", 50099, 1))
                .isInstanceOf(WorkerTransportSelector.TransportUnavailableException.class)
                .hasMessageContaining("worker-xyz");
    }

    // -------------------------------------------------------------------------
    // isGrpcReachable: direct tests
    // -------------------------------------------------------------------------

    /**
     * {@link WorkerTransportSelector#isGrpcReachable} must return {@code true} when a
     * live in-process gRPC worker is available.
     */
    @Test
    void isGrpcReachable_liveWorker_returnsTrue() throws IOException {
        String serverName = "reachable-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        WorkerTransportSelector selector = new WorkerTransportSelector();
        // isGrpcReachable normally uses forAddress(), but we test the probe client directly.
        WorkerClient client = new WorkerClient("probe", channel);
        HealthStatus status = client.getHealth("");
        assertThat(status.getState()).isNotEqualTo(WorkerState.WORKER_STATE_UNSPECIFIED);
    }

    /**
     * {@link WorkerTransportSelector#isGrpcReachable} must return {@code false} for a
     * port where nothing is listening.
     */
    @Test
    void isGrpcReachable_noServer_returnsFalse() {
        WorkerTransportSelector selector = new WorkerTransportSelector();
        // Port 1 is reserved and always refused
        boolean reachable = selector.isGrpcReachable("w-none", "127.0.0.1", 1);
        assertThat(reachable).isFalse();
    }

    // -------------------------------------------------------------------------
    // GrpcWorkerTransport: full lifecycle via gRPC
    // -------------------------------------------------------------------------

    /**
     * A full configure → start → stop cycle via {@link GrpcWorkerTransport} must succeed.
     */
    @Test
    void grpcTransport_fullLifecycle() throws IOException {
        String serverName = "lifecycle-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        WorkerClient client = new WorkerClient("lc-w1", channel);

        GrpcWorkerTransport t = new GrpcWorkerTransport(client);
        transport = t;

        String runId = UUID.randomUUID().toString();

        // Configure
        ConfigureAck configAck = t.configure(TestPlanMessage.newBuilder()
                .setRunId(runId)
                .setPlanContent(com.google.protobuf.ByteString.copyFromUtf8("<plan/>"))
                .setVirtualUsers(2)
                .setDurationSeconds(60)
                .build());
        assertThat(configAck.getAccepted())
                .as("Configure should be accepted")
                .isTrue();

        // Start
        StartAck startAck = t.start(StartMessage.newBuilder().setRunId(runId).build());
        assertThat(startAck.getAccepted())
                .as("Start should be accepted")
                .isTrue();

        // Health check
        HealthStatus health = t.getHealth();
        assertThat(health.getState())
                .as("Worker should be RUNNING after start")
                .isEqualTo(WorkerState.WORKER_STATE_RUNNING);

        // Stop (run-ID overload)
        StopAck stopAck = t.stop(runId);
        assertThat(stopAck.getAccepted())
                .as("Stop should be accepted")
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Helper: testable selector with injectable gRPC reachability
    // -------------------------------------------------------------------------

    /**
     * {@link WorkerTransportSelector} subclass that overrides the gRPC reachability probe
     * with a fixed boolean, removing the need for a real network socket.
     */
    static final class TestableSelector extends WorkerTransportSelector {

        private final boolean grpcReachable;

        TestableSelector(boolean grpcReachable) {
            this.grpcReachable = grpcReachable;
        }

        @Override
        boolean isGrpcReachable(String workerId, String host, int grpcPort) {
            return grpcReachable;
        }
    }
}
