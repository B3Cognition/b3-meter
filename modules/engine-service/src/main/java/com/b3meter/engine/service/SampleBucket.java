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
package com.b3meter.engine.service;

import java.time.Instant;
import java.util.Objects;

/**
 * A 1-second aggregation bucket for samples produced by a single sampler label.
 *
 * <p>The broker aggregates raw sample events into per-second buckets by label.
 * Each bucket carries the statistics computed over all samples in that one-second
 * window. Consumers (SSE publisher, dashboard, SLA evaluator) receive these buckets
 * via {@link SampleBucketConsumer} callbacks.
 *
 * <p>All response-time fields ({@code avgResponseTime}, {@code minResponseTime},
 * {@code maxResponseTime}, {@code percentile90}, {@code percentile95},
 * {@code percentile99}) are in milliseconds.
 *
 * <p>All numeric fields must be &gt;= 0. {@code sampleCount} must be &gt; 0 for a
 * non-empty bucket (a zero-count bucket represents a gap second with no activity).
 *
 * @param timestamp         start of the one-second window (truncated to second)
 * @param samplerLabel      label of the sampler that produced the samples
 * @param sampleCount       number of samples in this window; must be &gt;= 0
 * @param errorCount        number of error samples; must be &gt;= 0 and &lt;= {@code sampleCount}
 * @param avgResponseTime   mean response time in ms; must be &gt;= 0
 * @param minResponseTime   minimum response time in ms; must be &gt;= 0
 * @param maxResponseTime   maximum response time in ms; must be &gt;= {@code minResponseTime}
 * @param percentile90      90th-percentile response time in ms; must be &gt;= 0
 * @param percentile95      95th-percentile response time in ms; must be &gt;= 0
 * @param percentile99      99th-percentile response time in ms; must be &gt;= 0
 * @param samplesPerSecond    throughput in this window (same as {@code sampleCount} for 1-s buckets)
 * @param hdrHistogramBytes   serialized {@link HdrHistogramAccumulator} for distributed merge;
 *                            may be {@code null} when histogram data is not available
 */
public record SampleBucket(
        Instant timestamp,
        String samplerLabel,
        long sampleCount,
        long errorCount,
        double avgResponseTime,
        double minResponseTime,
        double maxResponseTime,
        double percentile90,
        double percentile95,
        double percentile99,
        double samplesPerSecond,
        byte[] hdrHistogramBytes
) {

    /**
     * Backward-compatible constructor without histogram data.
     * Delegates to the canonical constructor with {@code null} histogram bytes.
     */
    public SampleBucket(
            Instant timestamp,
            String samplerLabel,
            long sampleCount,
            long errorCount,
            double avgResponseTime,
            double minResponseTime,
            double maxResponseTime,
            double percentile90,
            double percentile95,
            double percentile99,
            double samplesPerSecond) {
        this(timestamp, samplerLabel, sampleCount, errorCount, avgResponseTime,
                minResponseTime, maxResponseTime, percentile90, percentile95, percentile99,
                samplesPerSecond, null);
    }

    /**
     * Compact canonical constructor — validates all fields.
     */
    public SampleBucket {
        Objects.requireNonNull(timestamp,    "timestamp must not be null");
        Objects.requireNonNull(samplerLabel, "samplerLabel must not be null");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0, got: " + sampleCount);
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must be >= 0, got: " + errorCount);
        }
        if (errorCount > sampleCount) {
            throw new IllegalArgumentException(
                    "errorCount (" + errorCount + ") must be <= sampleCount (" + sampleCount + ")");
        }
        if (avgResponseTime < 0) {
            throw new IllegalArgumentException("avgResponseTime must be >= 0, got: " + avgResponseTime);
        }
        if (minResponseTime < 0) {
            throw new IllegalArgumentException("minResponseTime must be >= 0, got: " + minResponseTime);
        }
        if (maxResponseTime < minResponseTime) {
            throw new IllegalArgumentException(
                    "maxResponseTime (" + maxResponseTime
                            + ") must be >= minResponseTime (" + minResponseTime + ")");
        }
        if (percentile90 < 0) {
            throw new IllegalArgumentException("percentile90 must be >= 0, got: " + percentile90);
        }
        if (percentile95 < 0) {
            throw new IllegalArgumentException("percentile95 must be >= 0, got: " + percentile95);
        }
        if (percentile99 < 0) {
            throw new IllegalArgumentException("percentile99 must be >= 0, got: " + percentile99);
        }
        if (samplesPerSecond < 0) {
            throw new IllegalArgumentException("samplesPerSecond must be >= 0, got: " + samplesPerSecond);
        }
    }

    /**
     * Computes the error rate as a percentage (0.0–100.0).
     *
     * @return 0.0 if no samples were collected; otherwise {@code (errorCount / sampleCount) * 100}
     */
    public double errorPercent() {
        if (sampleCount == 0) {
            return 0.0;
        }
        return (double) errorCount / sampleCount * 100.0;
    }
}
