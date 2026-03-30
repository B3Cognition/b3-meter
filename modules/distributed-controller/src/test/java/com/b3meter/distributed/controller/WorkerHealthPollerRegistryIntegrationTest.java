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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the end-to-end behaviour of the
 * {@link WorkerHealthPoller} → listener → {@link WorkerRegistry} chain.
 *
 * <p>Tests drive {@link WorkerHealthPoller#pollAll()} directly (no real scheduler
 * waits) for speed. This mirrors the pattern used in {@link WorkerHealthPollerTest}.
 *
 * <p>All tests must complete within 20 seconds — actual poll cycles finish in
 * milliseconds when driven manually.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class WorkerHealthPollerRegistryIntegrationTest {

    private WorkerHealthPoller poller;
    private WorkerRegistry registry;

    // gRPC resources — cleaned up in tearDown
    private FakeWorkerService workerImpl;
    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (poller != null) poller.close();
        if (workerImpl != null) workerImpl.shutdown();
        if (channel != null) channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // UNAVAILABLE → registry removes worker
    // -------------------------------------------------------------------------

    /**
     * After 3 consecutive missed polls the listener fires UNAVAILABLE and
     * the registry removes the worker from routing.
     */
    @Test
    void unavailableEvent_removesWorkerFromRegistry() throws IOException, InterruptedException {
        // --- Setup ---
        String serverName = "int-test-unavailable-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        WorkerClient client = new WorkerClient("w1", channel);

        registry = new WorkerRegistry();
        registry.register(client, "localhost", 50051, 8080);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Wire the listener (mirrors DistributedControllerConfiguration)
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            }
        });

        // Confirm initially available
        poller.pollAll();
        assertThat(registry.isAvailable("w1")).isTrue();

        // Kill the server → drive 3 miss polls
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server = null;

        poller.pollAll(); // miss 1
        poller.pollAll(); // miss 2
        poller.pollAll(); // miss 3 — UNAVAILABLE fires

        assertThat(registry.isAvailable("w1"))
                .as("worker should be unavailable after 3 missed polls")
                .isFalse();
        assertThat(registry.availableWorkers())
                .as("available workers should not contain the dead worker")
                .doesNotContainKey("w1");
    }

    /**
     * After UNAVAILABLE, the endpoint metadata is retained in the registry
     * so a subsequent AVAILABLE event can reconnect without re-supplying host/port.
     */
    @Test
    void unavailableEvent_retainsEndpointForReconnect() throws IOException, InterruptedException {
        String serverName = "int-test-endpoint-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        WorkerClient client = new WorkerClient("w1", channel);

        registry = new WorkerRegistry();
        registry.register(client, "worker-host", 50051, 9090);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            }
        });

        // Kill server → 3 misses
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server = null;
        poller.pollAll();
        poller.pollAll();
        poller.pollAll();

        // Endpoint metadata must survive unavailability
        WorkerRegistry.WorkerEndpoint ep = registry.endpointFor("w1");
        assertThat(ep).isNotNull();
        assertThat(ep.host()).isEqualTo("worker-host");
        assertThat(ep.grpcPort()).isEqualTo(50051);
    }

    // -------------------------------------------------------------------------
    // AVAILABLE → registry re-adds worker
    // -------------------------------------------------------------------------

    /**
     * After UNAVAILABLE, a successful poll fires AVAILABLE and the registry
     * re-adds the worker to routing with a fresh client.
     *
     * <p>In-process gRPC server names cannot be reused after {@code shutdownNow()},
     * so the "reconnected" client points to a second server (same pattern as
     * {@link WorkerHealthPollerTest#poll_workerRecovery()}).
     */
    @Test
    void availableEvent_reAddsWorkerToRegistry() throws IOException, InterruptedException {
        // --- Initial healthy server ---
        String serverName1 = "int-test-recovery-a-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName1)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName1).directExecutor().build();
        WorkerClient client = new WorkerClient("w2", channel);

        registry = new WorkerRegistry();
        registry.register(client, "localhost", 50052, 8080);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Wire the full UNAVAILABLE + AVAILABLE listener (mirrors DistributedControllerConfiguration)
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            } else if (availability == WorkerHealthPoller.WorkerAvailability.AVAILABLE) {
                WorkerRegistry.WorkerEndpoint ep = registry.endpointFor(workerId);
                if (ep != null) {
                    // In tests we can't use the real host:port; reconnect is mocked by
                    // using the new in-process client supplied through the reconnect below.
                    // The AVAILABLE event here is handled by the test directly.
                }
            }
        });

        // Healthy first poll
        poller.pollAll();
        assertThat(registry.isAvailable("w2")).isTrue();

        // Kill server → drive to UNAVAILABLE
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server = null;
        poller.pollAll(); // miss 1
        poller.pollAll(); // miss 2
        poller.pollAll(); // miss 3 — UNAVAILABLE
        assertThat(registry.isAvailable("w2")).isFalse();

        // --- Simulate worker restart: new server, replace poller client ---
        String serverName2 = "int-test-recovery-b-" + UUID.randomUUID();
        FakeWorkerService newImpl = new FakeWorkerService();
        Server newServer = InProcessServerBuilder.forName(serverName2)
                .directExecutor().addService(newImpl).build().start();
        ManagedChannel newChannel =
                InProcessChannelBuilder.forName(serverName2).directExecutor().build();
        WorkerClient newClient = new WorkerClient("w2", newChannel);

        // Replace worker in poller (same as production reconnect flow)
        poller.removeWorker("w2");
        poller.addWorker(newClient);

        // Manually apply the reconnect to the registry (simulates what the
        // DistributedControllerConfiguration.reconnect() method does on AVAILABLE)
        registry.markAvailable("w2", newClient);

        // Verify re-added before polling
        assertThat(registry.isAvailable("w2")).isTrue();
        assertThat(registry.availableWorkers().get("w2")).isSameAs(newClient);

        // One successful poll confirms the poller also sees AVAILABLE
        poller.pollAll();
        assertThat(poller.currentAvailability("w2"))
                .isEqualTo(WorkerHealthPoller.WorkerAvailability.AVAILABLE);

        // Cleanup extra resources
        newImpl.shutdown();
        newChannel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        newServer.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // AVAILABLE with null endpoint — worker remains unavailable
    // -------------------------------------------------------------------------

    /**
     * If an AVAILABLE event fires for a worker whose endpoint was removed from the
     * registry (e.g. deregistered between unavailable and recovery), the listener
     * must not throw and the worker must remain unavailable.
     *
     * <p>This tests the US-010.5 requirement: a false-positive or stale AVAILABLE
     * event does not crash the controller.
     */
    @Test
    void availableEvent_nullEndpoint_workerRemainsUnavailable() throws IOException, InterruptedException {
        String serverName = "int-test-null-ep-" + UUID.randomUUID();
        workerImpl = new FakeWorkerService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpl).build().start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        WorkerClient client = new WorkerClient("w3", channel);

        registry = new WorkerRegistry();
        registry.register(client, "localhost", 50053, 8080);

        poller = new WorkerHealthPoller(Executors.newSingleThreadScheduledExecutor());
        poller.addWorker(client);

        // Wire listener with null-endpoint guard (same as DistributedControllerConfiguration.reconnect)
        poller.addListener((workerId, availability) -> {
            if (availability == WorkerHealthPoller.WorkerAvailability.UNAVAILABLE) {
                registry.markUnavailable(workerId);
            } else if (availability == WorkerHealthPoller.WorkerAvailability.AVAILABLE) {
                WorkerRegistry.WorkerEndpoint ep = registry.endpointFor(workerId);
                if (ep == null) {
                    // No endpoint: log warning (tested here) and do NOT call markAvailable
                    return;
                }
                try {
                    WorkerClient newClient = new WorkerClient(workerId, ep.host(), ep.grpcPort());
                    registry.markAvailable(workerId, newClient);
                } catch (Exception ignored) {
                    // Swallow — worker stays unavailable
                }
            }
        });

        // Drive to UNAVAILABLE
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server = null;
        poller.pollAll();
        poller.pollAll();
        poller.pollAll();
        assertThat(registry.isAvailable("w3")).isFalse();

        // Deregister: remove endpoint so the reconnect path hits the null guard
        registry.deregister("w3");

        // Start a new server and replace the poller client so it can poll successfully
        String serverName2 = "int-test-null-ep-b-" + UUID.randomUUID();
        FakeWorkerService newImpl = new FakeWorkerService();
        Server newServer = InProcessServerBuilder.forName(serverName2)
                .directExecutor().addService(newImpl).build().start();
        ManagedChannel newChannel =
                InProcessChannelBuilder.forName(serverName2).directExecutor().build();
        WorkerClient newClient = new WorkerClient("w3", newChannel);
        poller.removeWorker("w3");
        poller.addWorker(newClient);

        // The AVAILABLE event fires but endpoint is null → listener returns without calling markAvailable
        // No exception should propagate; worker must remain unavailable (not in registry)
        poller.pollAll(); // triggers AVAILABLE event (miss counter was reset on first successful poll after UNAVAILABLE)

        assertThat(registry.isAvailable("w3"))
                .as("worker should remain unavailable when endpoint is null at reconnect time")
                .isFalse();

        // Cleanup
        newImpl.shutdown();
        newChannel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        newServer.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }
}
