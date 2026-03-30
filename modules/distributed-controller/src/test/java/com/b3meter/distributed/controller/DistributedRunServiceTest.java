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

import com.b3meter.engine.adapter.InMemorySampleStreamBroker;
import com.b3meter.engine.service.SampleBucket;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DistributedRunService} using in-process gRPC workers.
 *
 * <p>Each test stands up two {@link FakeWorkerService} instances wired via
 * {@link InProcessServerBuilder}. No Docker or real TCP ports are needed.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DistributedRunServiceTest {

    private static final byte[] DUMMY_PLAN =
            "<jmeterTestPlan/>".getBytes(StandardCharsets.UTF_8);

    // Worker 1
    private FakeWorkerService worker1Impl;
    private Server server1;
    private ManagedChannel channel1;
    private WorkerClient client1;

    // Worker 2
    private FakeWorkerService worker2Impl;
    private Server server2;
    private ManagedChannel channel2;
    private WorkerClient client2;

    private InMemorySampleStreamBroker broker;
    private ResultAggregator aggregator;
    private DistributedRunService service;

    @BeforeEach
    void setUp() throws IOException {
        // --- Worker 1 ---
        String serverName1 = "worker-1-" + UUID.randomUUID();
        worker1Impl = new FakeWorkerService();
        server1 = InProcessServerBuilder.forName(serverName1)
                .directExecutor().addService(worker1Impl).build().start();
        channel1 = InProcessChannelBuilder.forName(serverName1).directExecutor().build();
        client1 = new WorkerClient("worker-1", channel1);

        // --- Worker 2 ---
        String serverName2 = "worker-2-" + UUID.randomUUID();
        worker2Impl = new FakeWorkerService();
        server2 = InProcessServerBuilder.forName(serverName2)
                .directExecutor().addService(worker2Impl).build().start();
        channel2 = InProcessChannelBuilder.forName(serverName2).directExecutor().build();
        client2 = new WorkerClient("worker-2", channel2);

        // --- Service under test ---
        Map<String, WorkerClient> registry = new HashMap<>();
        registry.put("worker-1", client1);
        registry.put("worker-2", client2);

        broker = new InMemorySampleStreamBroker();
        aggregator = new ResultAggregator(broker);
        service = new DistributedRunService(registry, aggregator);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        worker1Impl.shutdown();
        worker2Impl.shutdown();
        channel1.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        channel2.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        server1.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        server2.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Configure + Start
    // -------------------------------------------------------------------------

    /**
     * Both workers should be configured and started; both should transition to RUNNING.
     */
    @Test
    void startDistributed_bothWorkersRunning() throws InterruptedException {
        String runId = service.startDistributed(
                "test-plan-1",
                List.of("worker-1", "worker-2"),
                DUMMY_PLAN,
                10,
                0L);

        assertThat(runId).isNotBlank();

        // Workers start at a coordinated future timestamp, but the fake transitions to
        // RUNNING on Start acceptance. The gRPC call is synchronous so by the time
        // startDistributed returns both workers should be RUNNING.
        waitForWorkerRunning(worker1Impl, 6_000L);
        waitForWorkerRunning(worker2Impl, 6_000L);

        assertThat(worker1Impl.currentState())
                .as("worker-1 should be RUNNING")
                .isEqualTo(FakeWorkerService.State.RUNNING);
        assertThat(worker2Impl.currentState())
                .as("worker-2 should be RUNNING")
                .isEqualTo(FakeWorkerService.State.RUNNING);

        assertThat(service.activeRunIds()).contains(runId);

        // Clean up
        service.stopDistributedNow(runId);
    }

    /**
     * Results from both workers should arrive at the aggregator's broker.
     */
    @Test
    void startDistributed_resultsCollectedFromBothWorkers() throws InterruptedException {
        List<SampleBucket> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2); // wait for at least 2 buckets

        String runId = service.startDistributed(
                "test-plan-2",
                List.of("worker-1", "worker-2"),
                DUMMY_PLAN,
                10,
                0L);

        // Subscribe to merged stream AFTER startDistributed (aggregator already subscribed workers)
        broker.subscribe(runId, bucket -> {
            received.add(bucket);
            latch.countDown();
        });

        // Wait for workers to emit at least one batch each (emit interval 3 s)
        boolean received2Buckets = latch.await(15, TimeUnit.SECONDS);

        service.stopDistributedNow(runId);

        assertThat(received2Buckets)
                .as("Should have received at least 2 sample buckets from workers")
                .isTrue();
        assertThat(received).isNotEmpty();
        assertThat(aggregator.totalSamples(runId)).isGreaterThan(0L);
    }

    // -------------------------------------------------------------------------
    // Stop (graceful)
    // -------------------------------------------------------------------------

    /**
     * Graceful stop should be accepted by both workers and remove the run record.
     */
    @Test
    void stopDistributed_acceptedByBothWorkers() throws InterruptedException {
        String runId = service.startDistributed(
                "test-plan-3",
                List.of("worker-1", "worker-2"),
                DUMMY_PLAN,
                4,
                0L);

        waitForWorkerRunning(worker1Impl, 6_000L);
        waitForWorkerRunning(worker2Impl, 6_000L);

        service.stopDistributed(runId);

        // After graceful stop the run record is removed
        assertThat(service.activeRunIds()).doesNotContain(runId);
    }

    // -------------------------------------------------------------------------
    // StopNow (immediate)
    // -------------------------------------------------------------------------

    /**
     * StopNow should immediately transition both workers back to IDLE.
     */
    @Test
    void stopDistributedNow_bothWorkersReturnToIdle() throws InterruptedException {
        String runId = service.startDistributed(
                "test-plan-4",
                List.of("worker-1", "worker-2"),
                DUMMY_PLAN,
                4,
                0L);

        waitForWorkerRunning(worker1Impl, 6_000L);
        waitForWorkerRunning(worker2Impl, 6_000L);

        service.stopDistributedNow(runId);

        assertThat(worker1Impl.currentState())
                .as("worker-1 should be IDLE after stopNow")
                .isEqualTo(FakeWorkerService.State.IDLE);
        assertThat(worker2Impl.currentState())
                .as("worker-2 should be IDLE after stopNow")
                .isEqualTo(FakeWorkerService.State.IDLE);
    }

    // -------------------------------------------------------------------------
    // Guard rails
    // -------------------------------------------------------------------------

    /**
     * Requesting a stop on an unknown run ID should throw.
     */
    @Test
    void stopDistributed_unknownRunIdThrows() {
        assertThatThrownBy(() -> service.stopDistributed("run-does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("run-does-not-exist");
    }

    /**
     * Starting with an unknown worker ID should throw.
     */
    @Test
    void startDistributed_unknownWorkerThrows() {
        assertThatThrownBy(() -> service.startDistributed(
                "plan-x", List.of("worker-99"), DUMMY_PLAN, 5, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker-99");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void waitForWorkerRunning(FakeWorkerService impl, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (impl.currentState() == FakeWorkerService.State.RUNNING) return;
            Thread.sleep(100);
        }
    }
}
