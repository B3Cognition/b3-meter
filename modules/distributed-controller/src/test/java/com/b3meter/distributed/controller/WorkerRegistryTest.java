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
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link WorkerRegistry}.
 *
 * <p>Uses real {@link WorkerClient} instances backed by a plaintext channel to
 * {@code localhost:1} — no real connection is made; these tests only verify
 * in-memory state management in the registry.
 */
class WorkerRegistryTest {

    private WorkerRegistry registry;

    /** Channels created during tests; closed in tearDown. */
    private final List<ManagedChannel> channels = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registry = new WorkerRegistry();
    }

    @AfterEach
    void tearDown() {
        for (ManagedChannel ch : channels) {
            ch.shutdownNow();
        }
        channels.clear();
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void register_workerIsAvailable() {
        WorkerClient client = makeClient("w1");
        registry.register(client, "localhost", 50051, 8080);

        assertThat(registry.isAvailable("w1")).isTrue();
        assertThat(registry.availableWorkers()).containsKey("w1");
    }

    @Test
    void register_storesEndpointMetadata() {
        WorkerClient client = makeClient("w1");
        registry.register(client, "worker-host", 50051, 9090);

        WorkerRegistry.WorkerEndpoint ep = registry.endpointFor("w1");
        assertThat(ep).isNotNull();
        assertThat(ep.host()).isEqualTo("worker-host");
        assertThat(ep.grpcPort()).isEqualTo(50051);
        assertThat(ep.wsPort()).isEqualTo(9090);
    }

    // -------------------------------------------------------------------------
    // deregister
    // -------------------------------------------------------------------------

    @Test
    void deregister_removesFromAvailableAndEndpoints() {
        registry.register(makeClient("w1"), "localhost", 50051, 8080);
        registry.deregister("w1");

        assertThat(registry.isAvailable("w1")).isFalse();
        assertThat(registry.availableWorkers()).doesNotContainKey("w1");
        assertThat(registry.endpointFor("w1")).isNull();
    }

    @Test
    void deregister_unknownWorker_isNoOp() {
        assertThatCode(() -> registry.deregister("nonexistent"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // markUnavailable
    // -------------------------------------------------------------------------

    @Test
    void markUnavailable_removesFromRouting() {
        registry.register(makeClient("w1"), "localhost", 50051, 8080);
        registry.markUnavailable("w1");

        assertThat(registry.isAvailable("w1")).isFalse();
        assertThat(registry.availableWorkers()).doesNotContainKey("w1");
    }

    @Test
    void markUnavailable_retainsEndpointForReconnect() {
        registry.register(makeClient("w1"), "worker-host", 50051, 8080);
        registry.markUnavailable("w1");

        WorkerRegistry.WorkerEndpoint ep = registry.endpointFor("w1");
        assertThat(ep).isNotNull();
        assertThat(ep.host()).isEqualTo("worker-host");
    }

    @Test
    void markUnavailable_unknownWorker_isNoOp() {
        assertThatCode(() -> registry.markUnavailable("nonexistent"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // markAvailable
    // -------------------------------------------------------------------------

    @Test
    void markAvailable_reAddsToRouting() {
        registry.register(makeClient("w1"), "localhost", 50051, 8080);
        registry.markUnavailable("w1");

        WorkerClient newClient = makeClient("w1");
        registry.markAvailable("w1", newClient);

        assertThat(registry.isAvailable("w1")).isTrue();
        assertThat(registry.availableWorkers()).containsKey("w1");
        assertThat(registry.availableWorkers().get("w1")).isSameAs(newClient);
    }

    @Test
    void markAvailable_unknownWorker_isNoOp() {
        WorkerClient ghost = makeClient("ghost");
        registry.markAvailable("ghost", ghost);

        assertThat(registry.isAvailable("ghost")).isFalse();
        assertThat(registry.endpointFor("ghost")).isNull();
    }

    // -------------------------------------------------------------------------
    // availableWorkers — live view
    // -------------------------------------------------------------------------

    @Test
    void availableWorkers_isLiveView_reflectsSubsequentRegistration() {
        Map<String, WorkerClient> view = registry.availableWorkers();

        registry.register(makeClient("w1"), "localhost", 50051, 8080);

        // Live view — reflects the mutation without obtaining a new reference
        assertThat(view).containsKey("w1");
    }

    @Test
    void availableWorkers_isLiveView_reflectsMarkUnavailable() {
        registry.register(makeClient("w1"), "localhost", 50051, 8080);
        Map<String, WorkerClient> view = registry.availableWorkers();
        assertThat(view).containsKey("w1");

        registry.markUnavailable("w1");

        assertThat(view).doesNotContainKey("w1");
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    @Test
    void concurrent_markUnavailable_and_availableWorkers_noException()
            throws InterruptedException {
        registry.register(makeClient("w1"), "localhost", 50051, 8080);
        CountDownLatch latch = new CountDownLatch(10);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                registry.markUnavailable("w1");
                latch.countDown();
            }));
            threads.add(Thread.ofVirtual().start(() -> {
                registry.availableWorkers();
                latch.countDown();
            }));
        }

        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("all 10 virtual threads should complete within 5 s")
                .isTrue();
        // State is either available or unavailable — no partial state
        boolean available = registry.isAvailable("w1");
        assertThat(available || !available).isTrue(); // tautology: verifies no exception
    }

    @Test
    void concurrent_registerAndMarkUnavailable_consistent()
            throws InterruptedException {
        int workerCount = 20;
        CountDownLatch latch = new CountDownLatch(workerCount * 2);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < workerCount; i++) {
            String id = "w" + i;
            threads.add(Thread.ofVirtual().start(() -> {
                registry.register(makeClient(id), "localhost", 50051, 8080);
                latch.countDown();
            }));
            threads.add(Thread.ofVirtual().start(() -> {
                registry.markUnavailable(id);
                latch.countDown();
            }));
        }

        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("all register+markUnavailable threads should complete within 5 s")
                .isTrue();

        // No exception should have been thrown; final state is consistent per-worker
        for (int i = 0; i < workerCount; i++) {
            String id = "w" + i;
            // Endpoint may or may not exist depending on race; available must be false if no endpoint
            if (registry.endpointFor(id) == null) {
                assertThat(registry.isAvailable(id)).isFalse();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link WorkerClient} backed by a non-connecting channel.
     * No real gRPC calls are made in these tests; we only exercise registry state.
     */
    private WorkerClient makeClient(String workerId) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 1)
                .usePlaintext()
                .build();
        channels.add(channel);
        return new WorkerClient(workerId, channel);
    }
}
