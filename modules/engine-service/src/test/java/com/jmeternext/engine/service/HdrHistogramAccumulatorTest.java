package com.jmeternext.engine.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HdrHistogramAccumulator}.
 */
class HdrHistogramAccumulatorTest {

    // =========================================================================
    // Basic recording and percentile
    // =========================================================================

    @Test
    void emptyHistogramReturnsZeroForAllPercentiles() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        assertEquals(0, hist.getPercentile(0));
        assertEquals(0, hist.getPercentile(50));
        assertEquals(0, hist.getPercentile(95));
        assertEquals(0, hist.getPercentile(99));
        assertEquals(0, hist.getPercentile(100));
        assertEquals(0, hist.getTotalCount());
    }

    @Test
    void singleValueReturnsItForAllPercentiles() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(42);

        assertEquals(1, hist.getTotalCount());
        assertEquals(42, hist.getPercentile(0));
        assertEquals(42, hist.getPercentile(50));
        assertEquals(42, hist.getPercentile(95));
        assertEquals(42, hist.getPercentile(100));
    }

    @Test
    void percentile50ReturnsMedian() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        // Record values 1–100
        for (int i = 1; i <= 100; i++) {
            hist.recordValue(i);
        }
        assertEquals(100, hist.getTotalCount());

        long p50 = hist.getPercentile(50);
        // Median of 1–100 should be 50
        assertEquals(50, p50);
    }

    @Test
    void percentile90ReturnsCorrectValue() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        for (int i = 1; i <= 100; i++) {
            hist.recordValue(i);
        }
        long p90 = hist.getPercentile(90);
        assertEquals(90, p90);
    }

    @Test
    void percentile95ReturnsCorrectValue() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        for (int i = 1; i <= 100; i++) {
            hist.recordValue(i);
        }
        long p95 = hist.getPercentile(95);
        assertEquals(95, p95);
    }

    @Test
    void percentile99ReturnsCorrectValue() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        for (int i = 1; i <= 100; i++) {
            hist.recordValue(i);
        }
        long p99 = hist.getPercentile(99);
        assertEquals(99, p99);
    }

    @Test
    void percentile100ReturnsMax() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        for (int i = 1; i <= 100; i++) {
            hist.recordValue(i);
        }
        assertEquals(100, hist.getPercentile(100));
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void negativeValuesClampedToZero() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(-5);
        assertEquals(1, hist.getTotalCount());
        assertEquals(0, hist.getPercentile(50));
        assertEquals(0, hist.getMin());
    }

    @Test
    void valuesAboveMaxClamped() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(100_000); // exceeds MAX_VALUE_MS (60000)
        assertEquals(1, hist.getTotalCount());
        assertEquals(60000, hist.getPercentile(100));
        assertEquals(60000, hist.getMax());
    }

    @Test
    void zeroValueRecorded() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(0);
        assertEquals(1, hist.getTotalCount());
        assertEquals(0, hist.getPercentile(50));
    }

    @Test
    void invalidPercentileThrows() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        assertThrows(IllegalArgumentException.class, () -> hist.getPercentile(-1));
        assertThrows(IllegalArgumentException.class, () -> hist.getPercentile(101));
    }

    // =========================================================================
    // Merge
    // =========================================================================

    @Test
    void mergeEmptyIntoEmpty() {
        HdrHistogramAccumulator a = new HdrHistogramAccumulator();
        HdrHistogramAccumulator b = new HdrHistogramAccumulator();
        a.merge(b);
        assertEquals(0, a.getTotalCount());
    }

    @Test
    void mergeAddsCountsCorrectly() {
        HdrHistogramAccumulator a = new HdrHistogramAccumulator();
        HdrHistogramAccumulator b = new HdrHistogramAccumulator();

        // Worker A: values 1–50
        for (int i = 1; i <= 50; i++) {
            a.recordValue(i);
        }
        // Worker B: values 51–100
        for (int i = 51; i <= 100; i++) {
            b.recordValue(i);
        }

        a.merge(b);

        assertEquals(100, a.getTotalCount());
        // After merge, percentiles should reflect the full 1–100 range
        assertEquals(50, a.getPercentile(50));
        assertEquals(95, a.getPercentile(95));
        assertEquals(99, a.getPercentile(99));
    }

    @Test
    void mergeDoesNotModifySource() {
        HdrHistogramAccumulator a = new HdrHistogramAccumulator();
        HdrHistogramAccumulator b = new HdrHistogramAccumulator();

        b.recordValue(100);
        b.recordValue(200);

        a.merge(b);

        assertEquals(2, b.getTotalCount()); // b unchanged
        assertEquals(2, a.getTotalCount());
    }

    @Test
    void mergeNullThrows() {
        HdrHistogramAccumulator a = new HdrHistogramAccumulator();
        assertThrows(NullPointerException.class, () -> a.merge(null));
    }

    // =========================================================================
    // Min, Max, Mean
    // =========================================================================

    @Test
    void minMaxOnEmptyHistogram() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        assertEquals(0, hist.getMin());
        assertEquals(0, hist.getMax());
        assertEquals(0.0, hist.getMean(), 0.001);
    }

    @Test
    void minMaxMeanCorrect() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(10);
        hist.recordValue(20);
        hist.recordValue(30);

        assertEquals(10, hist.getMin());
        assertEquals(30, hist.getMax());
        assertEquals(20.0, hist.getMean(), 0.001);
    }

    // =========================================================================
    // Reset
    // =========================================================================

    @Test
    void resetClearsAllData() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(42);
        hist.recordValue(100);
        assertEquals(2, hist.getTotalCount());

        hist.reset();

        assertEquals(0, hist.getTotalCount());
        assertEquals(0, hist.getPercentile(50));
        assertEquals(0, hist.getMin());
        assertEquals(0, hist.getMax());
    }

    // =========================================================================
    // toBucket
    // =========================================================================

    @Test
    void toBucketProducesValidBucket() {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        hist.recordValue(10);
        hist.recordValue(20);
        hist.recordValue(30);
        hist.recordValue(100);
        hist.recordValue(200);

        Instant now = Instant.now();
        SampleBucket bucket = hist.toBucket(now, "test-sampler", 1);

        assertEquals(now, bucket.timestamp());
        assertEquals("test-sampler", bucket.samplerLabel());
        assertEquals(5, bucket.sampleCount());
        assertEquals(1, bucket.errorCount());
        assertEquals(10, bucket.minResponseTime());
        assertEquals(200, bucket.maxResponseTime());
        assertTrue(bucket.percentile90() > 0);
        assertTrue(bucket.percentile95() > 0);
        assertTrue(bucket.percentile99() > 0);
        assertTrue(bucket.avgResponseTime() > 0);
    }

    // =========================================================================
    // SampleBucketAggregator integration
    // =========================================================================

    @Test
    void aggregatorRecordsAndFlushes() {
        SampleBucketAggregator agg = new SampleBucketAggregator();
        agg.recordSample("http-get", 50, false);
        agg.recordSample("http-get", 100, false);
        agg.recordSample("http-get", 200, true);
        agg.recordSample("http-post", 30, false);

        Instant now = Instant.now();
        Map<String, SampleBucket> buckets = agg.flush(now);

        assertEquals(2, buckets.size());
        assertTrue(buckets.containsKey("http-get"));
        assertTrue(buckets.containsKey("http-post"));

        SampleBucket get = buckets.get("http-get");
        assertEquals(3, get.sampleCount());
        assertEquals(1, get.errorCount());
        assertEquals("http-get", get.samplerLabel());

        SampleBucket post = buckets.get("http-post");
        assertEquals(1, post.sampleCount());
        assertEquals(0, post.errorCount());
    }

    @Test
    void aggregatorFlushResetsForNextWindow() {
        SampleBucketAggregator agg = new SampleBucketAggregator();
        agg.recordSample("label", 50, false);

        Instant t1 = Instant.now();
        Map<String, SampleBucket> first = agg.flush(t1);
        assertEquals(1, first.size());
        assertEquals(1, first.get("label").sampleCount());

        // After flush, histogram should be empty
        Instant t2 = t1.plusSeconds(1);
        Map<String, SampleBucket> second = agg.flush(t2);
        // No new samples recorded — should be empty
        assertTrue(second.isEmpty());
    }

    @Test
    void aggregatorMergesCrossWorker() {
        SampleBucketAggregator worker1 = new SampleBucketAggregator();
        SampleBucketAggregator worker2 = new SampleBucketAggregator();

        worker1.recordSample("api-call", 50, false);
        worker1.recordSample("api-call", 100, true);
        worker2.recordSample("api-call", 150, false);
        worker2.recordSample("api-call", 200, false);

        SampleBucketAggregator controller = new SampleBucketAggregator();
        controller.merge(worker1);
        controller.merge(worker2);

        HdrHistogramAccumulator hist = controller.getHistogram("api-call");
        assertNotNull(hist);
        assertEquals(4, hist.getTotalCount());
    }

    // =========================================================================
    // Concurrent recording
    // =========================================================================

    @Test
    void concurrentRecordingIsThreadSafe() throws InterruptedException {
        HdrHistogramAccumulator hist = new HdrHistogramAccumulator();
        int threads = 8;
        int recordsPerThread = 10_000;
        CountDownLatch latch = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    hist.recordValue(i % 1000);
                }
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();

        assertEquals((long) threads * recordsPerThread, hist.getTotalCount());
    }
}
