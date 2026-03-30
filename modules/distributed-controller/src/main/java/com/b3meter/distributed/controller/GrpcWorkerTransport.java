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

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.worker.proto.ConfigureAck;
import com.b3meter.worker.proto.HealthStatus;
import com.b3meter.worker.proto.SampleResultBatch;
import com.b3meter.worker.proto.StartAck;
import com.b3meter.worker.proto.StartMessage;
import com.b3meter.worker.proto.StopAck;
import com.b3meter.worker.proto.TestPlanMessage;

import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link WorkerTransport} implementation backed by an existing {@link WorkerClient}.
 *
 * <p>This is a thin adapter: every method delegates to the corresponding
 * {@link WorkerClient} method and translates the streaming callback from
 * {@link SampleResultBatch} (proto) to {@link SampleBucket} (engine model).
 *
 * <p>The underlying {@link WorkerClient} is owned by the caller.  Calling
 * {@link #close()} closes the channel; callers that share a {@link WorkerClient}
 * across multiple components should be careful not to close it prematurely.
 */
public final class GrpcWorkerTransport implements WorkerTransport {

    private static final Logger LOG = Logger.getLogger(GrpcWorkerTransport.class.getName());

    private final WorkerClient client;

    /**
     * Wraps the given {@link WorkerClient} as a {@link WorkerTransport}.
     *
     * @param client the gRPC client to delegate to; must not be {@code null}
     */
    public GrpcWorkerTransport(WorkerClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    // -------------------------------------------------------------------------
    // WorkerTransport implementation
    // -------------------------------------------------------------------------

    @Override
    public ConfigureAck configure(TestPlanMessage plan) {
        return client.configure(plan);
    }

    @Override
    public StartAck start(StartMessage msg) {
        return client.start(msg);
    }

    @Override
    public StopAck stop() {
        // WorkerClient.stop requires a runId; retrieve it from the active plan if possible.
        // When the caller hasn't set up a run yet, send an empty string — the worker will
        // reject it gracefully.
        return client.stop("");
    }

    /**
     * Stops the run identified by {@code runId}.
     *
     * <p>This overload is preferred over {@link #stop()} when the run ID is known.
     *
     * @param runId the run to stop
     * @return the worker's acknowledgement
     */
    public StopAck stop(String runId) {
        return client.stop(runId);
    }

    @Override
    public void streamResults(String runId, SampleBucketConsumer consumer) {
        client.streamResults(
                runId,
                batch -> {
                    try {
                        consumer.onBucket(toBucket(batch));
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "Error forwarding sample batch", ex);
                    }
                },
                () -> LOG.log(Level.INFO, "StreamResults completed for runId={0}", runId),
                err -> LOG.log(Level.WARNING,
                        "StreamResults error for runId={0}: {1}",
                        new Object[]{runId, err.getMessage()})
        );
    }

    @Override
    public HealthStatus getHealth() {
        return client.getHealth("");
    }

    @Override
    public void close() {
        client.close();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the underlying {@link WorkerClient}.
     *
     * @return the wrapped client; never {@code null}
     */
    public WorkerClient getClient() {
        return client;
    }

    // -------------------------------------------------------------------------
    // Proto → engine model conversion
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link SampleResultBatch} protobuf message to a {@link SampleBucket}.
     *
     * <p>Fields not present in the proto (min/max response time, samples-per-second) are
     * approximated from the available data.
     */
    private static SampleBucket toBucket(SampleResultBatch batch) {
        Instant ts = Instant.ofEpochSecond(
                batch.getTimestamp().getSeconds(),
                batch.getTimestamp().getNanos());

        double p90 = batch.getPercentilesOrDefault("p90", 0.0);
        double p95 = batch.getPercentilesOrDefault("p95", 0.0);
        double p99 = batch.getPercentilesOrDefault("p99", 0.0);

        return new SampleBucket(
                ts,
                batch.getSamplerLabel(),
                batch.getSampleCount(),
                batch.getErrorCount(),
                batch.getAvgResponseTime(),
                /* minResponseTime */   batch.getAvgResponseTime(),  // not in proto — approximate
                /* maxResponseTime */   p99 > 0 ? p99 : batch.getAvgResponseTime(),
                p90,
                p95,
                p99,
                /* samplesPerSecond */  (double) batch.getSampleCount()
        );
    }
}
