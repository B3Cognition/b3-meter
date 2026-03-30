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
package com.b3meter.web.api.service;

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.web.api.repository.SampleBucketRow;
import com.b3meter.web.api.repository.SampleResultRepository;
import com.b3meter.web.api.repository.TestRunRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subscribes to the {@link SampleStreamBroker} and persists incoming
 * {@link SampleBucket} events to the {@code sample_results} table in batches.
 *
 * <p>Each call to {@link #startWriting} registers a {@link SampleBucketConsumer}
 * for the given run. Received buckets are accumulated in an in-memory list and
 * flushed to {@link SampleResultRepository#insertBatch} as a single JDBC batch.
 * The flush is triggered either by {@link #stopWriting} (end of run) or when the
 * internal buffer exceeds the configured batch size threshold.
 *
 * <p><strong>Fault isolation:</strong> a {@link RuntimeException} thrown by the
 * database layer is caught, logged at WARNING level, and discarded. DB write
 * failures never propagate to the broker and therefore never interrupt the SSE
 * stream delivered to browser clients.
 *
 * <p><strong>Run completion:</strong> when {@link #stopWriting} is called, any
 * remaining buffered buckets are flushed first, then
 * {@link TestRunRepository#updateCompletion} is invoked to record the aggregated
 * {@code total_samples} and {@code error_count} on the {@code test_runs} row.
 *
 * <p><strong>Thread-safety:</strong> each per-run buffer is protected by a
 * dedicated monitor. The outer map is a {@link ConcurrentHashMap} — concurrent
 * start/stop calls for different runs are safe.
 */
@Service
public class SampleAggregationWriter {

    private static final Logger LOG = Logger.getLogger(SampleAggregationWriter.class.getName());

    /**
     * Number of buckets accumulated in memory before an intermediate flush is
     * triggered automatically. A value of {@code 60} covers a typical 1-minute
     * window; lower values trade throughput for lower peak memory usage.
     */
    static final int FLUSH_THRESHOLD = 60;

    private final SampleStreamBroker broker;
    private final SampleResultRepository sampleRepository;
    private final TestRunRepository runRepository;

    /** Active per-run write sessions. Key is runId. */
    private final ConcurrentHashMap<String, RunWriteSession> sessions = new ConcurrentHashMap<>();

    public SampleAggregationWriter(SampleStreamBroker broker,
                                   SampleResultRepository sampleRepository,
                                   TestRunRepository runRepository) {
        this.broker           = Objects.requireNonNull(broker,           "broker must not be null");
        this.sampleRepository = Objects.requireNonNull(sampleRepository, "sampleRepository must not be null");
        this.runRepository    = Objects.requireNonNull(runRepository,    "runRepository must not be null");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts persisting sample buckets for the given run.
     *
     * <p>Registers a {@link SampleBucketConsumer} with the broker. The consumer
     * remains registered until {@link #stopWriting} is called for the same run.
     * Calling {@code startWriting} twice for the same {@code runId} is a no-op
     * (the second call is silently ignored).
     *
     * @param runId the test run identifier; must not be null
     */
    public void startWriting(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        RunWriteSession session = new RunWriteSession(runId);
        RunWriteSession existing = sessions.putIfAbsent(runId, session);
        if (existing != null) {
            // Already registered — idempotent.
            return;
        }

        broker.subscribe(runId, session);
        LOG.info("SampleAggregationWriter: started writing for runId=" + runId);
    }

    /**
     * Stops persisting sample buckets for the given run.
     *
     * <p>Flushes any remaining buffered buckets to the database, then calls
     * {@link TestRunRepository#updateCompletion} with the aggregated totals.
     * If no session exists for the given {@code runId} this method is a no-op.
     *
     * @param runId        the test run identifier; must not be null
     * @param finalStatus  the terminal status to set on the run (e.g. {@code "COMPLETED"},
     *                     {@code "FAILED"}, {@code "STOPPED"}); must not be null
     */
    public void stopWriting(String runId, String finalStatus) {
        Objects.requireNonNull(runId,       "runId must not be null");
        Objects.requireNonNull(finalStatus, "finalStatus must not be null");

        RunWriteSession session = sessions.remove(runId);
        if (session == null) {
            return;
        }

        broker.unsubscribe(runId, session);
        session.flushRemaining();
        updateRunCompletion(runId, finalStatus, session.totalSamples, session.totalErrors);

        LOG.info("SampleAggregationWriter: stopped writing for runId=" + runId
                + " status=" + finalStatus
                + " totalSamples=" + session.totalSamples
                + " errorCount=" + session.totalErrors);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void updateRunCompletion(String runId, String status, long totalSamples, long errorCount) {
        try {
            runRepository.updateCompletion(runId, status, totalSamples, errorCount);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING,
                    "SampleAggregationWriter: failed to update run completion for runId={0}",
                    new Object[]{runId});
            LOG.log(Level.WARNING, "Run completion update failure detail", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Per-run write session
    // -------------------------------------------------------------------------

    /**
     * Holds the per-run buffer and accumulated totals.
     *
     * <p>All mutable state is guarded by {@code this} (the session instance).
     */
    final class RunWriteSession implements SampleBucketConsumer {

        private final String runId;

        /** Pending rows not yet flushed to the database. */
        private final List<SampleBucketRow> buffer = new ArrayList<>();

        /** Running total of all samples received, across all flushes. */
        volatile long totalSamples = 0L;

        /** Running total of all error samples received, across all flushes. */
        volatile long totalErrors  = 0L;

        RunWriteSession(String runId) {
            this.runId = runId;
        }

        // SampleBucketConsumer implementation — called by the broker from any thread.
        @Override
        public void onBucket(SampleBucket bucket) {
            SampleBucketRow row = toRow(bucket);
            boolean shouldFlush;

            synchronized (this) {
                buffer.add(row);
                totalSamples += bucket.sampleCount();
                totalErrors  += bucket.errorCount();
                shouldFlush   = buffer.size() >= FLUSH_THRESHOLD;
            }

            if (shouldFlush) {
                flushBuffer();
            }
        }

        /** Flushes any rows that remain in the buffer after the run ends. */
        void flushRemaining() {
            flushBuffer();
        }

        // -----------------------------------------------------------------
        // Private helpers
        // -----------------------------------------------------------------

        private void flushBuffer() {
            List<SampleBucketRow> snapshot;
            synchronized (this) {
                if (buffer.isEmpty()) {
                    return;
                }
                snapshot = new ArrayList<>(buffer);
                buffer.clear();
            }

            try {
                sampleRepository.insertBatch(runId, snapshot);
            } catch (RuntimeException ex) {
                // DB failure must not propagate to the broker (fault isolation).
                LOG.log(Level.WARNING,
                        "SampleAggregationWriter: DB insertBatch failed for runId={0}; {1} rows lost",
                        new Object[]{runId, snapshot.size()});
                LOG.log(Level.WARNING, "DB insertBatch failure detail", ex);
            }
        }

        private SampleBucketRow toRow(SampleBucket bucket) {
            return new SampleBucketRow(
                    runId,
                    bucket.timestamp(),
                    bucket.samplerLabel(),
                    bucket.sampleCount(),
                    bucket.errorCount(),
                    bucket.avgResponseTime(),
                    bucket.minResponseTime(),
                    bucket.maxResponseTime(),
                    bucket.percentile90(),
                    bucket.percentile95(),
                    bucket.percentile99(),
                    bucket.samplesPerSecond()
            );
        }
    }
}
