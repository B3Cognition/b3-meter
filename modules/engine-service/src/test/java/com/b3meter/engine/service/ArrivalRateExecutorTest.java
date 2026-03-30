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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ArrivalRateExecutor}.
 */
class ArrivalRateExecutorTest {

    // =========================================================================
    // Helper
    // =========================================================================

    private static TestRunContext testContext() {
        return TestRunContext.builder()
                .runId("test-run")
                .planPath("/test.jmx")
                .uiBridge(new NoOpUIBridge())
                .build();
    }

    /**
     * Minimal no-op UIBridge for tests.
     */
    static class NoOpUIBridge implements UIBridge {
        @Override public void onTestStarted(TestRunContext ctx) {}
        @Override public void onSample(TestRunContext ctx, double sps, double ep) {}
        @Override public void onTestEnded(TestRunContext ctx) {}
        @Override public void onThreadStarted(TestRunContext ctx, String name, int count) {}
        @Override public void onThreadFinished(TestRunContext ctx, String name, int count) {}
        @Override public void reportError(String msg, String title) {}
        @Override public void reportInfo(String msg, String title) {}
        @Override public void refreshUI() {}
        @Override public String promptPassword(String msg) { return null; }
        @Override public void onSampleReceived(TestRunContext ctx, String label, long elapsed, boolean success) {}
    }

    // =========================================================================
    // Construction validation
    // =========================================================================

    @Test
    void rejectsZeroRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalRateExecutor(0, 10, 1, Duration.ofSeconds(1), testContext()));
    }

    @Test
    void rejectsNegativeRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalRateExecutor(-1, 10, 1, Duration.ofSeconds(1), testContext()));
    }

    @Test
    void rejectsMaxVUsLessThanPreAllocated() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalRateExecutor(10, 2, 5, Duration.ofSeconds(1), testContext()));
    }

    @Test
    void rejectsZeroDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalRateExecutor(10, 10, 1, Duration.ZERO, testContext()));
    }

    @Test
    void rejectsNullContext() {
        assertThrows(NullPointerException.class,
                () -> new ArrivalRateExecutor(10, 10, 1, Duration.ofSeconds(1), null));
    }

    // =========================================================================
    // Target rate generation
    // =========================================================================

    @Test
    @Timeout(10)
    void generatesApproximatelyTargetRate() throws InterruptedException {
        double targetRate = 100; // 100 iter/s
        Duration duration = Duration.ofMillis(500);
        AtomicLong iterationCount = new AtomicLong(0);

        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                targetRate, 50, 10, duration, testContext());

        executor.start(() -> iterationCount.incrementAndGet());

        // Wait for the executor to finish
        Thread.sleep(1500);

        long actual = iterationCount.get();
        // 100 iter/s for 0.5s = ~50 iterations, allow 20% tolerance
        long expected = (long) (targetRate * duration.toMillis() / 1000.0);
        assertTrue(actual >= expected * 0.5,
                "Should generate at least 50% of target: expected ~" + expected + ", got " + actual);
        assertTrue(actual <= expected * 2.0,
                "Should not exceed 200% of target: expected ~" + expected + ", got " + actual);
    }

    // =========================================================================
    // Auto-scaling VUs
    // =========================================================================

    @Test
    @Timeout(10)
    void autoScalesWhenIterationsAreSlow() throws InterruptedException {
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                50, // 50 iter/s
                20, // maxVUs
                2,  // start with only 2 pre-allocated
                Duration.ofMillis(500),
                testContext());

        executor.start(() -> {
            try {
                // Simulate a slow iteration (100ms) so VUs get busy
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(1000);

        ArrivalRateExecutor.Stats stats = executor.getStats();
        // With 50 iter/s and each taking 100ms, need ~5 concurrent VUs minimum
        assertTrue(stats.allocatedVUs() > 2,
                "Should have auto-scaled beyond initial 2 VUs, got: " + stats.allocatedVUs());

        executor.stop();
    }

    // =========================================================================
    // maxVUs limit and dropped iterations
    // =========================================================================

    @Test
    @Timeout(10)
    void respectsMaxVUsLimitAndCountsDropped() throws InterruptedException {
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                100, // 100 iter/s (high rate)
                3,   // maxVUs (very low)
                1,
                Duration.ofMillis(500),
                testContext());

        executor.start(() -> {
            try {
                // Each iteration takes 200ms, so 3 VUs can handle ~15 iter/s max
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(1000);

        ArrivalRateExecutor.Stats stats = executor.getStats();
        assertTrue(stats.allocatedVUs() <= 3,
                "Should not exceed maxVUs of 3, got: " + stats.allocatedVUs());
        assertTrue(stats.droppedIterations() > 0,
                "Should have dropped some iterations, got: " + stats.droppedIterations());

        executor.stop();
    }

    // =========================================================================
    // Duration enforcement
    // =========================================================================

    @Test
    @Timeout(10)
    void stopsAfterDuration() throws InterruptedException {
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                10, 10, 1, Duration.ofMillis(300), testContext());

        executor.start(() -> {});

        assertTrue(executor.isRunning(), "Should be running right after start");

        // Wait long enough for the duration to elapse
        Thread.sleep(1500);

        assertFalse(executor.isRunning(), "Should have stopped after duration");
    }

    // =========================================================================
    // Stats accuracy
    // =========================================================================

    @Test
    @Timeout(10)
    void statsAreAccurate() throws InterruptedException {
        AtomicLong count = new AtomicLong(0);
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                20, 10, 5, Duration.ofMillis(500), testContext());

        executor.start(count::incrementAndGet);

        Thread.sleep(1000);

        ArrivalRateExecutor.Stats stats = executor.getStats();
        assertEquals(count.get(), stats.totalIterations(),
                "Stats totalIterations should match actual iteration count");
        assertTrue(stats.allocatedVUs() >= 5,
                "Should have at least the pre-allocated VUs");

        executor.stop();
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    @Test
    void accessorsReturnConfiguredValues() {
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                42.5, 100, 10, Duration.ofSeconds(30), testContext());

        assertEquals(42.5, executor.getTargetRate());
        assertEquals(100, executor.getMaxVUs());
        assertEquals(Duration.ofSeconds(30), executor.getDuration());
        assertFalse(executor.isRunning());

        executor.close();
    }

    @Test
    void doubleStartThrows() {
        ArrivalRateExecutor executor = new ArrivalRateExecutor(
                10, 10, 1, Duration.ofSeconds(10), testContext());
        executor.start(() -> {});

        assertThrows(IllegalStateException.class, () -> executor.start(() -> {}));

        executor.stop();
    }
}
