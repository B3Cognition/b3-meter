package com.jmeternext.distributed.controller;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkerHealthPoller} using in-process gRPC workers.
 *
 * <p>To avoid slow real-time polling delays (5 s intervals × 3 misses = 15 s) the
 * tests drive the poller's {@link WorkerHealthPoller#pollAll()} method directly
 * rather than waiting for the scheduler. This keeps each test well under 1 second
 * while still exercising all the availability-state logic.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class WorkerHealthPollerTest {

    private FakeWorkerService workerImpl;
    private Server server;
    private ManagedChannel channel;
    private WorkerClient client;
    private WorkerHealthPoller poller;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (poller != null) poller.close();
        if (workerImpl != null) workerImpl.shutdown();
        if (channel != null) channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Available worker
    // -------------------------------------------------------------------------

    /**
     * A healthy worker should be reported as AVAILABLE after a poll.
     */
    @Test
    void poll_healthyWorkerIsAvailable() throws IOException {
        String serverName = "health-poller-ok-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new WorkerClient("w1", channel);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Manually trigger one poll cycle
        poller.pollAll();

        assertThat(poller.currentAvailability("w1"))
                .as("healthy worker should be AVAILABLE")
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);
        assertThat(poller.missCount("w1")).isZero();
    }

    // -------------------------------------------------------------------------
    // Unavailable worker detection
    // -------------------------------------------------------------------------

    /**
     * After 3 consecutive missed polls (simulated by shutting down the server and
     * calling pollAll 3 times), the worker should be marked UNAVAILABLE.
     */
    @Test
    void poll_detectsUnavailableAfterThreeMisses() throws IOException, InterruptedException {
        String serverName = "health-poller-fail-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new WorkerClient("w2", channel);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // First poll succeeds → AVAILABLE
        poller.pollAll();
        assertThat(poller.currentAvailability("w2"))
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);

        // Shut down the server to simulate worker going away
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);

        // Three failed polls → UNAVAILABLE
        poller.pollAll(); // miss 1
        poller.pollAll(); // miss 2
        poller.pollAll(); // miss 3 — trips to UNAVAILABLE

        assertThat(poller.currentAvailability("w2"))
                .as("should be UNAVAILABLE after 3 misses")
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.UNAVAILABLE);
        assertThat(poller.missCount("w2"))
                .isGreaterThanOrEqualTo(WorkerHealthPoller.MAX_MISSED_HEARTBEATS);
    }

    // -------------------------------------------------------------------------
    // Listener notification
    // -------------------------------------------------------------------------

    /**
     * Registered listeners should be notified when availability transitions to UNAVAILABLE.
     */
    @Test
    void poll_notifiesListenerOnUnavailable() throws IOException, InterruptedException {
        String serverName = "health-poller-notify-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new WorkerClient("w3", channel);

        CopyOnWriteArrayList<WorkerHealthPoller.WorkerAvailability> events =
                new CopyOnWriteArrayList<>();
        CountDownLatch unavailableLatch = new CountDownLatch(1);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);
        poller.addListener((workerId, availability) -> {
            events.add(availability);
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                unavailableLatch.countDown();
            }
        });

        // Initial poll — healthy
        poller.pollAll();

        // Kill the server
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);

        // Drive 3 miss polls — listener should receive UNAVAILABLE event on the third
        poller.pollAll();
        poller.pollAll();
        poller.pollAll();

        assertThat(unavailableLatch.await(1, TimeUnit.SECONDS))
                .as("listener should have been notified of UNAVAILABLE within 1 s")
                .isTrue();
        assertThat(events).contains(WorkerHealthPoller.WorkerAvailability.UNAVAILABLE);
    }

    // -------------------------------------------------------------------------
    // Recovery: worker comes back
    // -------------------------------------------------------------------------

    /**
     * If a worker comes back up, the poller should detect it once the client is
     * reconnected and an availability event fires.
     *
     * <p>In-process gRPC server names cannot be reused after {@code shutdownNow()}, so
     * this test simulates "reconnect" by replacing the {@link WorkerClient} in the
     * poller with a new one pointing to a freshly started server. This faithfully
     * models the real-world scenario where the controller retries with a new channel
     * after a worker comes back.
     */
    @Test
    void poll_workerRecovery() throws IOException, InterruptedException {
        // --- Initial healthy server ---
        String serverName1 = "health-poller-recovery-a-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName1)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName1).directExecutor().build();
        client = new WorkerClient("w4", channel);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Confirm initially AVAILABLE
        poller.pollAll();
        assertThat(poller.currentAvailability("w4"))
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);

        // --- Kill the server → drive to UNAVAILABLE ---
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        poller.pollAll(); // miss 1
        poller.pollAll(); // miss 2
        poller.pollAll(); // miss 3 — UNAVAILABLE
        assertThat(poller.currentAvailability("w4"))
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.UNAVAILABLE);

        // --- Simulate worker restart: new server + replace client in poller ---
        String serverName2 = "health-poller-recovery-b-" + UUID.randomUUID();
        FakeWorkerService newImpl = new FakeWorkerService();
        Server newServer = InProcessServerBuilder.forName(serverName2)
                .directExecutor().addService(newImpl).build().start();
        ManagedChannel newChannel =
                InProcessChannelBuilder.forName(serverName2).directExecutor().build();
        WorkerClient newClient = new WorkerClient("w4", newChannel);

        // Replace worker in the poller (remove old, add new)
        poller.removeWorker("w4");
        poller.addWorker(newClient);

        // One successful poll → back to AVAILABLE
        poller.pollAll();

        assertThat(poller.currentAvailability("w4"))
                .as("worker should be AVAILABLE after reconnect and successful poll")
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);

        // Cleanup extra resources created in this test
        newImpl.shutdown();
        newChannel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        newServer.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Scheduler-driven start/stop cycle
    // -------------------------------------------------------------------------

    /**
     * The start/stop lifecycle of the poller should not throw and should clean up cleanly.
     */
    @Test
    void schedulerDrivenPolling_startsAndStopsCleanly() throws IOException {
        String serverName = "health-poller-sched-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new WorkerClient("w5", channel);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Start then immediately stop — should not throw
        poller.start();
        poller.stop();

        // Direct manual poll call to verify logic is operational
        poller.pollAll();

        assertThat(poller.currentAvailability("w5"))
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);
    }
}
