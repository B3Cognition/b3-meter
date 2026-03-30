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
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopMessage;
import com.b3meter.worker.proto.TestPlanMessage;
import com.b3meter.worker.proto.WorkerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResultAggregator} with 3 in-process gRPC worker streams.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Batches from all 3 workers arrive at the unified broker.</li>
 *   <li>The aggregated {@link ResultAggregator#totalSamples(String)} equals the sum of
 *       per-worker sample counts.</li>
 * </ul>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ResultAggregatorTest {

    private static final byte[] DUMMY_PLAN =
            "<jmeterTestPlan/>".getBytes(StandardCharsets.UTF_8);

    private final FakeWorkerService[] workerImpls = new FakeWorkerService[3];
    private final Server[] servers = new Server[3];
    private final ManagedChannel[] channels = new ManagedChannel[3];
    private final WorkerClient[] clients = new WorkerClient[3];

    @AfterEach
    void tearDown() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            if (workerImpls[i] != null) workerImpls[i].shutdown();
            if (channels[i] != null) channels[i].shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            if (servers[i] != null) servers[i].shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Merge 3 worker streams — verify total counts
    // -------------------------------------------------------------------------

    /**
     * Subscribing 3 workers verifies that the aggregated total sample count equals
     * the sum of what each worker emitted.
     *
     * <p>Each {@link FakeWorkerService} emits {@value FakeWorkerService#SAMPLES_PER_BATCH}
     * samples per batch at a {@value FakeWorkerService#EMIT_INTERVAL_MS} ms interval.
     * We collect at least {@code minBatches} batches (one per worker) and check the total.
     */
    @Test
    void mergeThreeWorkerStreams_totalCountEqualsSum() throws IOException, InterruptedException {
        // Set up 3 in-process workers
        for (int i = 0; i < 3; i++) {
            String serverName = "aggregator-worker-" + i + "-" + UUID.randomUUID();
            workerImpls[i] = new FakeWorkerService();
            servers[i] = InProcessServerBuilder.forName(serverName)
                    .directExecutor().addService(workerImpls[i]).build().start();
            channels[i] = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            clients[i] = new WorkerClient("agg-worker-" + i, channels[i]);
        }

        String runId = UUID.randomUUID().toString();

        // Configure and start all 3 workers
        for (int i = 0; i < 3; i++) {
            WorkerServiceGrpc.WorkerServiceBlockingStub stub =
                    WorkerServiceGrpc.newBlockingStub(channels[i])
                            .withDeadlineAfter(10, TimeUnit.SECONDS);
            stub.configure(TestPlanMessage.newBuilder()
                    .setRunId(runId)
                    .setPlanContent(com.google.protobuf.ByteString.copyFrom(DUMMY_PLAN))
                    .setVirtualUsers(5)
                    .setDurationSeconds(0)
                    .build());
            stub.start(StartMessage.newBuilder().setRunId(runId).build());
        }

        // Build aggregator with a shared broker
        InMemorySampleStreamBroker broker = new InMemorySampleStreamBroker();
        ResultAggregator aggregator = new ResultAggregator(broker);

        // Collect all buckets published to the broker
        List<SampleBucket> allBuckets = new CopyOnWriteArrayList<>();
        int minBuckets = 3; // one from each worker
        CountDownLatch latch = new CountDownLatch(minBuckets);
        broker.subscribe(runId, bucket -> {
            allBuckets.add(bucket);
            latch.countDown();
        });

        // Subscribe all 3 workers via the aggregator (this opens the streaming RPCs)
        for (int i = 0; i < 3; i++) {
            aggregator.subscribeWorker(runId, clients[i]);
        }

        // Wait up to 15 s for minimum batch count
        boolean received = latch.await(15, TimeUnit.SECONDS);

        // Stop all workers
        for (int i = 0; i < 3; i++) {
            try {
                WorkerServiceGrpc.newBlockingStub(channels[i])
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .stopNow(StopMessage.newBuilder().setRunId(runId).build());
            } catch (Exception ignored) {}
        }

        // Assertions
        assertThat(received)
                .as("Should have received at least %d buckets from 3 workers", minBuckets)
                .isTrue();

        assertThat(allBuckets)
                .as("allBuckets should contain at least %d entries", minBuckets)
                .hasSizeGreaterThanOrEqualTo(minBuckets);

        // Aggregated total must equal the sum of individual bucket sampleCounts
        long sumFromBuckets = allBuckets.stream()
                .mapToLong(SampleBucket::sampleCount)
                .sum();
        assertThat(aggregator.totalSamples(runId))
                .as("aggregator totalSamples should equal sum of bucket sampleCounts")
                .isEqualTo(sumFromBuckets);

        // Each worker emits SAMPLES_PER_BATCH samples per batch (10)
        assertThat(aggregator.totalSamples(runId) % FakeWorkerService.SAMPLES_PER_BATCH)
                .as("totalSamples should be a multiple of SAMPLES_PER_BATCH")
                .isZero();
    }

    // -------------------------------------------------------------------------
    // Single worker stream
    // -------------------------------------------------------------------------

    /**
     * With a single worker, {@link ResultAggregator#totalSamples(String)} should
     * reflect all emitted batches.
     */
    @Test
    void singleWorkerStream_totalCountAccumulates() throws IOException, InterruptedException {
        String serverName = "aggregator-single-" + UUID.randomUUID();
        workerImpls[0] = new FakeWorkerService();
        servers[0] = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpls[0]).build().start();
        channels[0] = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        clients[0] = new WorkerClient("single-worker", channels[0]);

        String runId = UUID.randomUUID().toString();
        WorkerServiceGrpc.WorkerServiceBlockingStub stub =
                WorkerServiceGrpc.newBlockingStub(channels[0])
                        .withDeadlineAfter(10, TimeUnit.SECONDS);
        stub.configure(TestPlanMessage.newBuilder()
                .setRunId(runId)
                .setPlanContent(com.google.protobuf.ByteString.copyFrom(DUMMY_PLAN))
                .setVirtualUsers(1)
                .setDurationSeconds(0)
                .build());
        stub.start(StartMessage.newBuilder().setRunId(runId).build());

        InMemorySampleStreamBroker broker = new InMemorySampleStreamBroker();
        ResultAggregator aggregator = new ResultAggregator(broker);

        CountDownLatch latch = new CountDownLatch(1);
        broker.subscribe(runId, bucket -> latch.countDown());
        aggregator.subscribeWorker(runId, clients[0]);

        assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("Should receive at least one bucket")
                .isTrue();

        stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .stopNow(StopMessage.newBuilder().setRunId(runId).build());

        assertThat(aggregator.totalSamples(runId))
                .as("should have accumulated at least one batch of samples")
                .isGreaterThanOrEqualTo(FakeWorkerService.SAMPLES_PER_BATCH);
    }

    // -------------------------------------------------------------------------
    // clearRun resets counters
    // -------------------------------------------------------------------------

    /**
     * {@link ResultAggregator#clearRun(String)} should reset both sample and error counters.
     */
    @Test
    void clearRun_resetsCounters() throws IOException, InterruptedException {
        String serverName = "aggregator-clear-" + UUID.randomUUID();
        workerImpls[0] = new FakeWorkerService();
        servers[0] = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(workerImpls[0]).build().start();
        channels[0] = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        clients[0] = new WorkerClient("clear-worker", channels[0]);

        String runId = UUID.randomUUID().toString();
        WorkerServiceGrpc.WorkerServiceBlockingStub stub =
                WorkerServiceGrpc.newBlockingStub(channels[0])
                        .withDeadlineAfter(10, TimeUnit.SECONDS);
        stub.configure(TestPlanMessage.newBuilder()
                .setRunId(runId)
                .setPlanContent(com.google.protobuf.ByteString.copyFrom(DUMMY_PLAN))
                .setVirtualUsers(1)
                .setDurationSeconds(0)
                .build());
        stub.start(StartMessage.newBuilder().setRunId(runId).build());

        InMemorySampleStreamBroker broker = new InMemorySampleStreamBroker();
        ResultAggregator aggregator = new ResultAggregator(broker);

        CountDownLatch latch = new CountDownLatch(1);
        broker.subscribe(runId, bucket -> latch.countDown());
        aggregator.subscribeWorker(runId, clients[0]);

        latch.await(10, TimeUnit.SECONDS);
        stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .stopNow(StopMessage.newBuilder().setRunId(runId).build());

        assertThat(aggregator.totalSamples(runId)).isGreaterThan(0L);

        aggregator.clearRun(runId);

        assertThat(aggregator.totalSamples(runId))
                .as("totalSamples should be 0 after clearRun")
                .isZero();
        assertThat(aggregator.totalErrors(runId))
                .as("totalErrors should be 0 after clearRun")
                .isZero();
    }
}
