package com.jmeternext.engine.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Detects coordinated omission — a measurement bias that occurs when the load
 * generator slows down because the system under test is slow, thereby hiding
 * the true latency experienced by would-be requests.
 *
 * <p>When a load generator sends requests at a fixed interval (e.g., every 10ms)
 * but one request takes 500ms, the generator "omits" ~49 samples that would have
 * been sent during that gap. Without correction, the reported percentiles
 * understate the real user experience because those phantom requests are not
 * counted.
 *
 * <p>This detector monitors inter-bucket timestamps. When the actual interval
 * between consecutive {@link SampleBucket}s exceeds twice the expected interval,
 * a gap is flagged and the number of missed (phantom) samples is estimated.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CoordinatedOmissionDetector detector = new CoordinatedOmissionDetector(10.0);
 * broker.subscribe(runId, detector::onBucket);
 * // ... after test ...
 * CoordinatedOmissionReport report = detector.getReport();
 * }</pre>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public class CoordinatedOmissionDetector implements SampleBucketConsumer {

    private static final Logger LOG = Logger.getLogger(CoordinatedOmissionDetector.class.getName());

    private final double expectedIntervalMs;
    private final AtomicLong gapCount = new AtomicLong();
    private final AtomicLong phantomSamples = new AtomicLong();
    private volatile Instant lastSampleTime;

    // Track worst gap for reporting
    private volatile double worstGapMs;

    /**
     * Constructs a detector with the given expected interval between samples.
     *
     * @param expectedIntervalMs expected interval in milliseconds between
     *                           consecutive sample buckets (e.g., 1000.0 for
     *                           1-second buckets, or the target request interval)
     * @throws IllegalArgumentException if expectedIntervalMs is not positive
     */
    public CoordinatedOmissionDetector(double expectedIntervalMs) {
        if (expectedIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "expectedIntervalMs must be positive, got: " + expectedIntervalMs);
        }
        this.expectedIntervalMs = expectedIntervalMs;
    }

    /**
     * Processes a sample bucket and checks for coordinated omission gaps.
     *
     * <p>A gap is detected when the actual interval between this bucket and the
     * previous one exceeds twice the expected interval. The number of phantom
     * (missed) samples is estimated as {@code (actualInterval / expectedInterval) - 1}.
     *
     * @param bucket the completed bucket; never {@code null}
     */
    @Override
    public void onBucket(SampleBucket bucket) {
        Instant now = bucket.timestamp();
        Instant previous = lastSampleTime;

        if (previous != null) {
            double actualIntervalMs = Duration.between(previous, now).toMillis();
            if (actualIntervalMs > expectedIntervalMs * 2) {
                long missed = (long) (actualIntervalMs / expectedIntervalMs) - 1;
                gapCount.incrementAndGet();
                phantomSamples.addAndGet(missed);

                if (actualIntervalMs > worstGapMs) {
                    worstGapMs = actualIntervalMs;
                }

                LOG.warning("Coordinated omission: gap of " + actualIntervalMs + "ms " +
                        "(expected " + expectedIntervalMs + "ms), ~" + missed + " phantom samples");
            }
        }

        lastSampleTime = now;
    }

    /**
     * Returns the current coordinated omission report.
     *
     * @return a snapshot of detected gaps and phantom sample estimates
     */
    public CoordinatedOmissionReport getReport() {
        return new CoordinatedOmissionReport(
                gapCount.get(),
                phantomSamples.get(),
                worstGapMs,
                expectedIntervalMs
        );
    }

    /**
     * Resets the detector state for reuse.
     */
    public void reset() {
        gapCount.set(0);
        phantomSamples.set(0);
        lastSampleTime = null;
        worstGapMs = 0;
    }

    /**
     * Report summarizing coordinated omission detection results.
     *
     * @param gapCount          number of detected gaps (intervals &gt; 2x expected)
     * @param phantomSamples    estimated number of missed/phantom samples across all gaps
     * @param worstGapMs        duration of the longest gap in milliseconds
     * @param expectedIntervalMs the configured expected interval in milliseconds
     */
    public record CoordinatedOmissionReport(
            long gapCount,
            long phantomSamples,
            double worstGapMs,
            double expectedIntervalMs
    ) {

        /**
         * Returns {@code true} if any coordinated omission was detected.
         */
        public boolean hasOmission() {
            return gapCount > 0;
        }

        /**
         * Returns a human-readable summary of the report.
         */
        @Override
        public String toString() {
            if (!hasOmission()) {
                return "CoordinatedOmissionReport: no coordinated omission detected";
            }
            return String.format(
                    "CoordinatedOmissionReport: %d gaps, ~%d phantom samples, " +
                            "worst gap %.1fms (expected interval %.1fms)",
                    gapCount, phantomSamples, worstGapMs, expectedIntervalMs);
        }
    }
}
