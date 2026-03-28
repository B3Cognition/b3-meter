package com.jmeternext.engine.service.shape;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all built-in {@link LoadShape} implementations.
 */
class LoadShapeTest {

    // =========================================================================
    // ConstantShape
    // =========================================================================

    @Test
    void constantShapeReturnsCorrectTickForDuration() {
        ConstantShape shape = new ConstantShape(100, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        tick = shape.tick(Duration.ofSeconds(5), 50);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        tick = shape.tick(Duration.ofSeconds(10), 100);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());
    }

    @Test
    void constantShapeReturnsNullAfterDuration() {
        ConstantShape shape = new ConstantShape(100, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(11), 100);
        assertNull(tick, "Should return null after duration elapses");
    }

    @Test
    void constantShapeRejectsNegativeUsers() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConstantShape(-1, Duration.ofSeconds(10)));
    }

    @Test
    void constantShapeZeroUsers() {
        ConstantShape shape = new ConstantShape(0, Duration.ofSeconds(5));
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(1), 0);
        assertNotNull(tick);
        assertEquals(0, tick.targetUsers());
    }

    // =========================================================================
    // RampShape
    // =========================================================================

    @Test
    void rampShapeAtZeroPercent() {
        RampShape shape = new RampShape(0, 200, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(0, tick.targetUsers());
    }

    @Test
    void rampShapeAtFiftyPercent() {
        RampShape shape = new RampShape(0, 200, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(5), 0);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());
    }

    @Test
    void rampShapeAtHundredPercent() {
        RampShape shape = new RampShape(0, 200, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(10), 0);
        assertNotNull(tick);
        assertEquals(200, tick.targetUsers());
    }

    @Test
    void rampShapeReturnsNullAfterDuration() {
        RampShape shape = new RampShape(0, 200, Duration.ofSeconds(10));

        assertNull(shape.tick(Duration.ofSeconds(11), 0),
                "Should return null after ramp duration");
    }

    @Test
    void rampShapeRampDown() {
        RampShape shape = new RampShape(100, 0, Duration.ofSeconds(10));

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(5), 100);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());
    }

    @Test
    void rampShapeSpawnRateIsPositive() {
        RampShape shape = new RampShape(0, 100, Duration.ofSeconds(10));
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertTrue(tick.spawnRate() > 0, "Spawn rate should be positive");
    }

    @Test
    void rampShapeRejectsZeroDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RampShape(0, 100, Duration.ZERO));
    }

    // =========================================================================
    // StagesShape
    // =========================================================================

    @Test
    void stagesShapeTransitionsBetweenStages() {
        StagesShape shape = new StagesShape(List.of(
                new StagesShape.Stage(Duration.ofSeconds(10), 100),  // 0 -> 100 over 10s
                new StagesShape.Stage(Duration.ofSeconds(10), 100),  // hold at 100 for 10s
                new StagesShape.Stage(Duration.ofSeconds(10), 0)     // 100 -> 0 over 10s
        ));

        // At start: should be 0 (ramping from 0 to 100)
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(0, tick.targetUsers());

        // Midpoint of stage 1: should be ~50
        tick = shape.tick(Duration.ofSeconds(5), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // End of stage 1: should be 100
        tick = shape.tick(Duration.ofSeconds(10), 0);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // Midpoint of stage 2 (hold): should still be 100
        tick = shape.tick(Duration.ofSeconds(15), 100);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // Midpoint of stage 3 (ramp down): should be ~50
        tick = shape.tick(Duration.ofSeconds(25), 100);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // End of stage 3: should be 0
        tick = shape.tick(Duration.ofSeconds(30), 0);
        assertNotNull(tick);
        assertEquals(0, tick.targetUsers());
    }

    @Test
    void stagesShapeReturnsNullAfterAllStages() {
        StagesShape shape = new StagesShape(List.of(
                new StagesShape.Stage(Duration.ofSeconds(5), 50)
        ));

        assertNull(shape.tick(Duration.ofSeconds(6), 50),
                "Should return null after all stages complete");
    }

    @Test
    void stagesShapeRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new StagesShape(List.of()));
    }

    @Test
    void stagesShapeRejectsNullList() {
        assertThrows(NullPointerException.class,
                () -> new StagesShape(null));
    }

    // =========================================================================
    // SinusoidalShape
    // =========================================================================

    @Test
    void sinusoidalShapeOscillatesBetweenMinAndMax() {
        SinusoidalShape shape = new SinusoidalShape(
                10, 100, Duration.ofSeconds(60), Duration.ofMinutes(10));

        // At t=0: sin(0) = 0, so users = mid = 55
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(55, tick.targetUsers());

        // At t=15s (quarter period): sin(PI/2) = 1, so users = mid + amp = 100
        tick = shape.tick(Duration.ofSeconds(15), 55);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // At t=30s (half period): sin(PI) ≈ 0, so users ≈ mid = 55
        tick = shape.tick(Duration.ofSeconds(30), 100);
        assertNotNull(tick);
        assertEquals(55, tick.targetUsers());

        // At t=45s (3/4 period): sin(3*PI/2) = -1, so users = mid - amp = 10
        tick = shape.tick(Duration.ofSeconds(45), 55);
        assertNotNull(tick);
        assertEquals(10, tick.targetUsers());
    }

    @Test
    void sinusoidalShapeReturnsNullAfterDuration() {
        SinusoidalShape shape = new SinusoidalShape(
                10, 100, Duration.ofSeconds(60), Duration.ofSeconds(120));

        assertNull(shape.tick(Duration.ofSeconds(121), 55),
                "Should return null after total duration");
    }

    @Test
    void sinusoidalShapeSpawnRateIsAtLeastOne() {
        SinusoidalShape shape = new SinusoidalShape(
                50, 50, Duration.ofSeconds(10), Duration.ofSeconds(60));

        // Amplitude is 0, so derivative-based rate would be 0, but clamped to 1.0
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertTrue(tick.spawnRate() >= 1.0, "Spawn rate should be at least 1.0");
    }

    @Test
    void sinusoidalShapeRejectsMaxLessThanMin() {
        assertThrows(IllegalArgumentException.class,
                () -> new SinusoidalShape(100, 10, Duration.ofSeconds(10), Duration.ofMinutes(1)));
    }

    // =========================================================================
    // StepShape
    // =========================================================================

    @Test
    void stepShapeStepsUpAtCorrectIntervals() {
        StepShape shape = new StepShape(10, Duration.ofSeconds(5), 50, Duration.ofMinutes(1));

        // At t=0: step 1 -> 10 users
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(10, tick.targetUsers());

        // At t=5: step 2 -> 20 users
        tick = shape.tick(Duration.ofSeconds(5), 10);
        assertNotNull(tick);
        assertEquals(20, tick.targetUsers());

        // At t=10: step 3 -> 30 users
        tick = shape.tick(Duration.ofSeconds(10), 20);
        assertNotNull(tick);
        assertEquals(30, tick.targetUsers());

        // At t=20: step 5 -> 50 users (capped at max)
        tick = shape.tick(Duration.ofSeconds(20), 40);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // At t=25: step 6 -> would be 60, capped at 50
        tick = shape.tick(Duration.ofSeconds(25), 50);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());
    }

    @Test
    void stepShapeReturnsNullAfterDuration() {
        StepShape shape = new StepShape(10, Duration.ofSeconds(5), 100, Duration.ofSeconds(30));

        assertNull(shape.tick(Duration.ofSeconds(31), 0),
                "Should return null after total duration");
    }

    @Test
    void stepShapeRejectsZeroStepSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepShape(0, Duration.ofSeconds(5), 100, Duration.ofMinutes(1)));
    }

    // =========================================================================
    // CompositeShape
    // =========================================================================

    @Test
    void compositeShapeChainsShapesCorrectly() {
        // Chain: RampShape (0->100 over 10s), ConstantShape (100 for 10s)
        RampShape ramp = new RampShape(0, 100, Duration.ofSeconds(10));
        ConstantShape constant = new ConstantShape(100, Duration.ofSeconds(10));

        CompositeShape composite = new CompositeShape(
                List.of(ramp, constant),
                List.of(Duration.ofSeconds(10), Duration.ofSeconds(10))
        );

        // During ramp phase (t=5): should be ramping up
        LoadShape.ShapeTick tick = composite.tick(Duration.ofSeconds(5), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // During constant phase (t=15): should be constant at 100
        tick = composite.tick(Duration.ofSeconds(15), 100);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());
    }

    @Test
    void compositeShapeReturnsNullAfterAllShapes() {
        ConstantShape shape = new ConstantShape(50, Duration.ofSeconds(5));

        CompositeShape composite = new CompositeShape(
                List.of(shape),
                List.of(Duration.ofSeconds(5))
        );

        assertNull(composite.tick(Duration.ofSeconds(6), 0),
                "Should return null after all shapes complete");
    }

    @Test
    void compositeShapePassesRelativeElapsedTime() {
        // Second shape starts at t=10, should receive elapsed time starting from 0
        ConstantShape first = new ConstantShape(50, Duration.ofSeconds(10));
        RampShape second = new RampShape(0, 100, Duration.ofSeconds(10));

        CompositeShape composite = new CompositeShape(
                List.of(first, second),
                List.of(Duration.ofSeconds(10), Duration.ofSeconds(10))
        );

        // At t=10 (boundary — still in first shape due to <= comparison):
        // ConstantShape returns 50
        LoadShape.ShapeTick tick = composite.tick(Duration.ofSeconds(10), 50);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // At t=11 (now in second shape, elapsed=1 relative): ramp at 1/10 progress -> ~10
        tick = composite.tick(Duration.ofSeconds(11), 50);
        assertNotNull(tick);
        assertEquals(10, tick.targetUsers());

        // At t=15 (midpoint of second shape, elapsed=5): ramp at 5/10 = 50% -> 50
        tick = composite.tick(Duration.ofSeconds(15), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());
    }

    @Test
    void compositeShapeRejectsMismatchedSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompositeShape(
                        List.of(new ConstantShape(10, Duration.ofSeconds(5))),
                        List.of(Duration.ofSeconds(5), Duration.ofSeconds(10))
                ));
    }

    @Test
    void compositeShapeRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompositeShape(List.of(), List.of()));
    }

    // =========================================================================
    // ShapeTick validation
    // =========================================================================

    @Test
    void shapeTickRejectsNegativeUsers() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoadShape.ShapeTick(-1, 1.0));
    }

    @Test
    void shapeTickRejectsNegativeSpawnRate() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoadShape.ShapeTick(10, -1.0));
    }
}
