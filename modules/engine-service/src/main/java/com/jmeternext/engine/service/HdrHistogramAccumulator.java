package com.jmeternext.engine.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe HDR Histogram accumulator for accurate percentile computation.
 *
 * <p>Uses a simple array-based histogram with 1ms buckets covering the range
 * 0–60,000ms (60 seconds). This avoids external dependencies while providing
 * mergeable, accurate percentile computation suitable for distributed
 * aggregation across controller/worker nodes.
 *
 * <p>Unlike pre-computed p90/p95/p99 values stored in {@link SampleBucket},
 * histograms can be merged across workers without loss of accuracy. The
 * controller collects per-worker histograms and merges them into a global
 * histogram for the final percentile computation.
 *
 * <h2>Thread Safety</h2>
 * All public methods are synchronized. The {@link #totalCount} field uses
 * {@link AtomicLong} for lock-free reads when only the count is needed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
 * hist.recordValue(42);
 * hist.recordValue(150);
 * long p95 = hist.getPercentile(95.0);
 *
 * // Merge from another worker
 * HdrHistogramAccumulator workerHist = ...;
 * hist.merge(workerHist);
 * }</pre>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public class HdrHistogramAccumulator {

    /** Maximum recordable value in milliseconds (60 seconds). */
    public static final int MAX_VALUE_MS = 60_000;

    private final long[] counts;  // 1ms buckets, 0–60000ms range
    private final AtomicLong totalCount = new AtomicLong();

    /**
     * Constructs an empty histogram with buckets for 0–60,000ms.
     */
    public HdrHistogramAccumulator() {
        this.counts = new long[MAX_VALUE_MS + 1]; // 0–60000 inclusive
    }

    /**
     * Records a single response-time value.
     *
     * <p>Values exceeding {@link #MAX_VALUE_MS} are clamped to {@code MAX_VALUE_MS}.
     * Negative values are clamped to 0.
     *
     * @param valueMs response time in milliseconds
     */
    public synchronized void recordValue(long valueMs) {
        int bucket = (int) Math.max(0, Math.min(valueMs, MAX_VALUE_MS));
        counts[bucket]++;
        totalCount.incrementAndGet();
    }

    /**
     * Returns the value at the given percentile.
     *
     * <p>For an empty histogram (no recorded values), returns 0.
     *
     * @param percentile the percentile to compute (0.0–100.0)
     * @return the value at the given percentile in milliseconds
     * @throws IllegalArgumentException if percentile is outside [0, 100]
     */
    public synchronized long getPercentile(double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException(
                    "percentile must be in [0, 100], got: " + percentile);
        }
        long total = totalCount.get();
        if (total == 0) {
            return 0;
        }
        long target = (long) Math.ceil(total * percentile / 100.0);
        if (target == 0) {
            target = 1;
        }
        long cumulative = 0;
        for (int i = 0; i < counts.length; i++) {
            cumulative += counts[i];
            if (cumulative >= target) {
                return i;
            }
        }
        return MAX_VALUE_MS;
    }

    /**
     * Merges another histogram into this one for distributed aggregation.
     *
     * <p>After merging, this histogram contains the combined counts from both
     * histograms. The {@code other} histogram is not modified.
     *
     * @param other the histogram to merge; must not be {@code null}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public synchronized void merge(HdrHistogramAccumulator other) {
        if (other == null) {
            throw new NullPointerException("other must not be null");
        }
        synchronized (other) {
            for (int i = 0; i < counts.length; i++) {
                counts[i] += other.counts[i];
            }
            totalCount.addAndGet(other.totalCount.get());
        }
    }

    /**
     * Returns the total number of recorded values.
     *
     * @return count of recorded values
     */
    public long getTotalCount() {
        return totalCount.get();
    }

    /**
     * Returns the minimum recorded value, or 0 if the histogram is empty.
     *
     * @return minimum value in milliseconds
     */
    public synchronized long getMin() {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Returns the maximum recorded value, or 0 if the histogram is empty.
     *
     * @return maximum value in milliseconds
     */
    public synchronized long getMax() {
        for (int i = counts.length - 1; i >= 0; i--) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Returns the mean of all recorded values, or 0.0 if the histogram is empty.
     *
     * @return mean value in milliseconds
     */
    public synchronized double getMean() {
        long total = totalCount.get();
        if (total == 0) {
            return 0.0;
        }
        double sum = 0;
        for (int i = 0; i < counts.length; i++) {
            sum += (double) i * counts[i];
        }
        return sum / total;
    }

    /**
     * Resets the histogram to its initial empty state.
     */
    public synchronized void reset() {
        java.util.Arrays.fill(counts, 0);
        totalCount.set(0);
    }

    /**
     * Creates a {@link SampleBucket} from this histogram's data.
     *
     * @param timestamp    the bucket timestamp
     * @param samplerLabel the sampler label
     * @param errorCount   the number of errors in this window
     * @return a fully populated SampleBucket
     */
    public synchronized SampleBucket toBucket(
            java.time.Instant timestamp,
            String samplerLabel,
            long errorCount) {
        long total = totalCount.get();
        return new SampleBucket(
                timestamp,
                samplerLabel,
                total,
                errorCount,
                getMean(),
                getMin(),
                getMax(),
                getPercentile(90.0),
                getPercentile(95.0),
                getPercentile(99.0),
                total // samplesPerSecond = total for 1s buckets
        );
    }
}
