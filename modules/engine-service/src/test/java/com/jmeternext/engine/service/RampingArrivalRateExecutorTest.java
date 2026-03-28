package com.jmeternext.engine.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RampingArrivalRateExecutor}.
 */
class RampingArrivalRateExecutorTest {

    // =========================================================================
    // Helper
    // =========================================================================

    private static TestRunContext testContext() {
        return TestRunContext.builder()
                .runId("test-run")
                .planPath("/test.jmx")
                .uiBridge(new ArrivalRateExecutorTest.NoOpUIBridge())
                .build();
    }

    // =========================================================================
    // Construction validation
    // =========================================================================

    @Test
    void rejectsNegativeStartRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new RampingArrivalRateExecutor(-1, 10, 1, testContext(),
                        List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(1), 10))));
    }

    @Test
    void rejectsEmptyStages() {
        assertThrows(IllegalArgumentException.class,
                () -> new RampingArrivalRateExecutor(10, 10, 1, testContext(), List.of()));
    }

    @Test
    void rejectsNullStages() {
        assertThrows(NullPointerException.class,
                () -> new RampingArrivalRateExecutor(10, 10, 1, testContext(), null));
    }

    // =========================================================================
    // Rate interpolation (unit tests on the pure function)
    // =========================================================================

    @Test
    void interpolationAtStartReturnsStartRate() {
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 50, 5, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 50.0)));

        double rate = executor.interpolateRate(0);
        assertEquals(10.0, rate, 0.01, "Rate at t=0 should be startRate");

        executor.close();
    }

    @Test
    void interpolationAtEndOfStageReturnsTargetRate() {
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 50, 5, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 50.0)));

        double rate = executor.interpolateRate(Duration.ofSeconds(10).toNanos());
        assertEquals(50.0, rate, 0.01, "Rate at end of stage should be targetRate");

        executor.close();
    }

    @Test
    void interpolationAtMidpointReturnsAverage() {
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 50, 5, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 50.0)));

        double rate = executor.interpolateRate(Duration.ofSeconds(5).toNanos());
        assertEquals(30.0, rate, 0.01, "Rate at midpoint should be average of start and target");

        executor.close();
    }

    @Test
    void interpolationAcrossMultipleStages() {
        // Stage 1: 10 -> 50 over 10s, Stage 2: 50 -> 20 over 10s
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 50, 5, testContext(),
                List.of(
                        new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 50.0),
                        new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 20.0)
                ));

        // At the boundary (10s): should be at the first stage's target = 50
        double rateAtBoundary = executor.interpolateRate(Duration.ofSeconds(10).toNanos());
        assertEquals(50.0, rateAtBoundary, 0.01, "Rate at stage boundary should be stage 1 target");

        // At 15s (midpoint of stage 2): 50 -> 20, midpoint = 35
        double rateAtMid = executor.interpolateRate(Duration.ofSeconds(15).toNanos());
        assertEquals(35.0, rateAtMid, 0.01, "Rate at midpoint of stage 2 should be 35");

        // At 20s (end of stage 2): should be 20
        double rateAtEnd = executor.interpolateRate(Duration.ofSeconds(20).toNanos());
        assertEquals(20.0, rateAtEnd, 0.01, "Rate at end of stage 2 should be 20");

        executor.close();
    }

    @Test
    void interpolationPastAllStagesHoldsLastRate() {
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 50, 5, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(5), 30.0)));

        double rate = executor.interpolateRate(Duration.ofSeconds(100).toNanos());
        assertEquals(30.0, rate, 0.01, "Rate past all stages should hold at last target");

        executor.close();
    }

    @Test
    void interpolationRampDown() {
        // Start at 100, ramp down to 0
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                100.0, 50, 5, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 0.0)));

        double rateAt25 = executor.interpolateRate(Duration.ofMillis(2500).toNanos());
        assertEquals(75.0, rateAt25, 0.5, "Rate at 25% should be ~75");

        double rateAt75 = executor.interpolateRate(Duration.ofMillis(7500).toNanos());
        assertEquals(25.0, rateAt75, 0.5, "Rate at 75% should be ~25");

        executor.close();
    }

    // =========================================================================
    // Smooth transitions (integration-level)
    // =========================================================================

    @Test
    @Timeout(10)
    void transitionsSmoothlyBetweenStages() throws InterruptedException {
        AtomicLong count = new AtomicLong(0);

        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                5.0, 20, 5, testContext(),
                List.of(
                        new RampingArrivalRateExecutor.Stage(Duration.ofMillis(250), 20.0),
                        new RampingArrivalRateExecutor.Stage(Duration.ofMillis(250), 20.0)
                ));

        executor.start(count::incrementAndGet);

        // Wait for the executor to finish (total 500ms)
        Thread.sleep(1500);

        assertFalse(executor.isRunning(), "Should have stopped after all stages");
        assertTrue(count.get() > 0, "Should have dispatched some iterations");

        executor.close();
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    @Test
    void accessorsReturnConfiguredValues() {
        List<RampingArrivalRateExecutor.Stage> stages = List.of(
                new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 50.0),
                new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(20), 10.0)
        );

        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                5.0, 100, 10, testContext(), stages);

        assertEquals(5.0, executor.getStartRate());
        assertEquals(2, executor.getStages().size());
        assertEquals(Duration.ofSeconds(30), executor.getTotalDuration());
        assertFalse(executor.isRunning());

        executor.close();
    }

    @Test
    void doubleStartThrows() {
        RampingArrivalRateExecutor executor = new RampingArrivalRateExecutor(
                10.0, 10, 1, testContext(),
                List.of(new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(10), 20.0)));

        executor.start(() -> {});
        assertThrows(IllegalStateException.class, () -> executor.start(() -> {}));

        executor.stop();
    }

    // =========================================================================
    // Stage record validation
    // =========================================================================

    @Test
    void stageRejectsNegativeRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new RampingArrivalRateExecutor.Stage(Duration.ofSeconds(1), -1.0));
    }

    @Test
    void stageRejectsZeroDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RampingArrivalRateExecutor.Stage(Duration.ZERO, 10.0));
    }

    @Test
    void stageRejectsNullDuration() {
        assertThrows(NullPointerException.class,
                () -> new RampingArrivalRateExecutor.Stage(null, 10.0));
    }
}
