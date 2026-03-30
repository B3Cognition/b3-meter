package com.jmeternext.engine.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe HDR Histogram accumulator for accurate percentile computation.
 *
 * <p>Uses a simple array-based histogram with 1ms buckets covering the range
 * 0–120,000ms (120 seconds). This avoids external dependencies while providing
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

    /** Maximum recordable value in milliseconds (120 seconds). */
    public static final int MAX_VALUE_MS = 120_000;

    private final long[] counts;  // 1ms buckets, 0–120000ms range
    private final AtomicLong totalCount = new AtomicLong();

    /**
     * Constructs an empty histogram with buckets for 0–120,000ms.
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

    // -------------------------------------------------------------------------
    // Serialization for distributed histogram merge
    // -------------------------------------------------------------------------

    /**
     * Serializes this histogram to a compact byte array using sparse run-length encoding.
     *
     * <p>Format (big-endian):
     * <pre>
     *   [4 bytes] totalCount (int, clamped from long)
     *   [repeating for each non-zero bucket]:
     *     [4 bytes] bucketIndex (int, 0–120000)
     *     [4 bytes] count (int, clamped from long)
     * </pre>
     *
     * <p>Only non-zero buckets are serialized, making this very compact for typical
     * distributions which have fewer than 100 non-zero buckets out of 120,001.
     *
     * @return the serialized byte array; never {@code null}
     */
    public synchronized byte[] toBytes() {
        // Count non-zero buckets for precise allocation
        int nonZero = 0;
        for (long count : counts) {
            if (count > 0) nonZero++;
        }

        // 4 bytes header (totalCount) + 8 bytes per non-zero bucket (4 index + 4 count)
        ByteBuffer buf = ByteBuffer.allocate(4 + nonZero * 8);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt((int) Math.min(totalCount.get(), Integer.MAX_VALUE));

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                buf.putInt(i);
                buf.putInt((int) Math.min(counts[i], Integer.MAX_VALUE));
            }
        }

        return buf.array();
    }

    /**
     * Deserializes a histogram from a byte array produced by {@link #toBytes()}.
     *
     * @param data the serialized byte array; must not be {@code null}
     * @return a new {@link HdrHistogramAccumulator} populated with the deserialized data
     * @throws NullPointerException     if {@code data} is {@code null}
     * @throws IllegalArgumentException if {@code data} is too short or malformed
     */
    public static HdrHistogramAccumulator fromBytes(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data must not be null");
        }
        if (data.length < 4) {
            throw new IllegalArgumentException(
                    "data too short: expected at least 4 bytes, got " + data.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);

        int totalCount = buf.getInt();
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();

        while (buf.remaining() >= 8) {
            int bucketIndex = buf.getInt();
            int count = buf.getInt();
            if (bucketIndex >= 0 && bucketIndex < hist.counts.length && count > 0) {
                hist.counts[bucketIndex] = count;
            }
        }

        hist.totalCount.set(totalCount);
        return hist;
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
                total, // samplesPerSecond = total for 1s buckets
                toBytes() // serialize histogram for distributed merge
        );
    }
}
