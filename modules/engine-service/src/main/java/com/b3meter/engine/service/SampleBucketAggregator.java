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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates raw sample response times into per-label {@link HdrHistogramAccumulator}
 * instances, enabling accurate percentile computation and distributed merging.
 *
 * <p>Each sampler label gets its own histogram. When a 1-second aggregation window
 * closes, call {@link #flush(Instant)} to produce {@link SampleBucket} records from
 * all active histograms, then reset them for the next window.
 *
 * <p>For distributed mode, the controller can merge worker aggregators via
 * {@link #merge(SampleBucketAggregator)}.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. The underlying {@link ConcurrentHashMap} and
 * synchronized histogram operations ensure safe concurrent recording.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public class SampleBucketAggregator {

    private final ConcurrentHashMap<String, HdrHistogramAccumulator> histograms =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> errorCounts =
            new ConcurrentHashMap<>();

    /**
     * Records a sample response time for the given sampler label.
     *
     * @param samplerLabel the sampler label; must not be {@code null}
     * @param responseTimeMs response time in milliseconds
     * @param isError whether this sample is an error
     */
    public void recordSample(String samplerLabel, long responseTimeMs, boolean isError) {
        Objects.requireNonNull(samplerLabel, "samplerLabel must not be null");
        histograms
                .computeIfAbsent(samplerLabel, k -> new HdrHistogramAccumulator())
                .recordValue(responseTimeMs);
        if (isError) {
            errorCounts.merge(samplerLabel, 1L, Long::sum);
        }
    }

    /**
     * Returns the histogram for the given label, or {@code null} if no samples
     * have been recorded for that label.
     *
     * @param samplerLabel the sampler label
     * @return the histogram, or null
     */
    public HdrHistogramAccumulator getHistogram(String samplerLabel) {
        return histograms.get(samplerLabel);
    }

    /**
     * Produces a {@link SampleBucket} for each active label, then resets all
     * histograms for the next aggregation window.
     *
     * @param timestamp the start of the aggregation window
     * @return map of sampler label to SampleBucket
     */
    public Map<String, SampleBucket> flush(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Map<String, SampleBucket> result = new ConcurrentHashMap<>();

        for (Map.Entry<String, HdrHistogramAccumulator> entry : histograms.entrySet()) {
            String label = entry.getKey();
            HdrHistogramAccumulator hist = entry.getValue();
            long errors = errorCounts.getOrDefault(label, 0L);

            if (hist.getTotalCount() > 0) {
                result.put(label, hist.toBucket(timestamp, label, errors));
            }
        }

        // Reset for next window
        histograms.values().forEach(HdrHistogramAccumulator::reset);
        errorCounts.clear();

        return result;
    }

    /**
     * Merges all histograms from another aggregator into this one.
     * Used by the controller to combine worker results in distributed mode.
     *
     * @param other the aggregator to merge from; must not be {@code null}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public void merge(SampleBucketAggregator other) {
        Objects.requireNonNull(other, "other must not be null");
        for (Map.Entry<String, HdrHistogramAccumulator> entry : other.histograms.entrySet()) {
            histograms
                    .computeIfAbsent(entry.getKey(), k -> new HdrHistogramAccumulator())
                    .merge(entry.getValue());
            long otherErrors = other.errorCounts.getOrDefault(entry.getKey(), 0L);
            if (otherErrors > 0) {
                errorCounts.merge(entry.getKey(), otherErrors, Long::sum);
            }
        }
    }

    /**
     * Returns the set of sampler labels that have recorded at least one sample.
     *
     * @return unmodifiable set of labels
     */
    public java.util.Set<String> getLabels() {
        return java.util.Collections.unmodifiableSet(histograms.keySet());
    }
}
