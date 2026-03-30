package com.jmeternext.engine.service.shape;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ShapeParser}.
 */
class ShapeParserTest {

    // =========================================================================
    // Constant shape
    // =========================================================================

    @Test
    void parsesConstantShapeWithSeconds() {
        LoadShape shape = ShapeParser.parse("constant:50:60s");
        assertInstanceOf(ConstantShape.class, shape);

        // At t=0: should return 50 users
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // At t=60: still valid (within duration)
        tick = shape.tick(Duration.ofSeconds(60), 50);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // At t=61: should return null (past duration)
        assertNull(shape.tick(Duration.ofSeconds(61), 50));
    }

    @Test
    void parsesConstantShapeWithMinutes() {
        LoadShape shape = ShapeParser.parse("constant:100:5m");
        assertInstanceOf(ConstantShape.class, shape);

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // Should still be active at 4 minutes
        tick = shape.tick(Duration.ofMinutes(4), 100);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // Should be null after 5 minutes
        assertNull(shape.tick(Duration.ofMinutes(5).plusSeconds(1), 100));
    }

    @Test
    void parsesConstantShapeWithBareDuration() {
        // Bare number (no suffix) treated as seconds
        LoadShape shape = ShapeParser.parse("constant:25:30");
        assertInstanceOf(ConstantShape.class, shape);

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(15), 0);
        assertNotNull(tick);
        assertEquals(25, tick.targetUsers());
    }

    // =========================================================================
    // Ramp shape
    // =========================================================================

    @Test
    void parsesRampShape() {
        LoadShape shape = ShapeParser.parse("ramp:0:100:60s");
        assertInstanceOf(RampShape.class, shape);

        // At t=0: 0 users
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(0, tick.targetUsers());

        // At t=30: ~50 users
        tick = shape.tick(Duration.ofSeconds(30), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // At t=60: 100 users
        tick = shape.tick(Duration.ofSeconds(60), 0);
        assertNotNull(tick);
        assertEquals(100, tick.targetUsers());

        // After duration: null
        assertNull(shape.tick(Duration.ofSeconds(61), 100));
    }

    @Test
    void parsesRampShapeRampDown() {
        LoadShape shape = ShapeParser.parse("ramp:100:0:10s");
        assertInstanceOf(RampShape.class, shape);

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(5), 100);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());
    }

    // =========================================================================
    // Stages shape
    // =========================================================================

    @Test
    void parsesStagesShape() {
        LoadShape shape = ShapeParser.parse("stages:0:10:30s,10:50:60s,50:0:30s");
        assertInstanceOf(StagesShape.class, shape);

        // Stage 1: ramp 0 -> 10 over 30s. At t=15: ~5 users
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(15), 0);
        assertNotNull(tick);
        assertEquals(5, tick.targetUsers());

        // End of stage 1 (t=30): 10 users
        tick = shape.tick(Duration.ofSeconds(30), 0);
        assertNotNull(tick);
        assertEquals(10, tick.targetUsers());

        // Mid stage 2 (t=60): ~30 users (ramping 10 -> 50 over 60s, at 30s in)
        tick = shape.tick(Duration.ofSeconds(60), 10);
        assertNotNull(tick);
        assertEquals(30, tick.targetUsers());

        // End of stage 2 (t=90): 50 users
        tick = shape.tick(Duration.ofSeconds(90), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());

        // Mid stage 3 (t=105): ~25 users (ramping 50 -> 0 over 30s, at 15s in)
        tick = shape.tick(Duration.ofSeconds(105), 50);
        assertNotNull(tick);
        assertEquals(25, tick.targetUsers());

        // After all stages: null
        assertNull(shape.tick(Duration.ofSeconds(121), 0));
    }

    @Test
    void parsesStagesShapeSingleStage() {
        LoadShape shape = ShapeParser.parse("stages:0:100:60s");
        assertInstanceOf(StagesShape.class, shape);

        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(30), 0);
        assertNotNull(tick);
        assertEquals(50, tick.targetUsers());
    }

    // =========================================================================
    // Step shape
    // =========================================================================

    @Test
    void parsesStepShape() {
        LoadShape shape = ShapeParser.parse("step:10:15s:100:120s");
        assertInstanceOf(StepShape.class, shape);

        // At t=0: step 1 -> 10 users
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(10, tick.targetUsers());

        // At t=15: step 2 -> 20 users
        tick = shape.tick(Duration.ofSeconds(15), 10);
        assertNotNull(tick);
        assertEquals(20, tick.targetUsers());

        // At t=30: step 3 -> 30 users
        tick = shape.tick(Duration.ofSeconds(30), 20);
        assertNotNull(tick);
        assertEquals(30, tick.targetUsers());

        // Past duration: null
        assertNull(shape.tick(Duration.ofSeconds(121), 100));
    }

    // =========================================================================
    // Sine shape
    // =========================================================================

    @Test
    void parsesSineShape() {
        LoadShape shape = ShapeParser.parse("sine:10:100:30s:120s");
        assertInstanceOf(SinusoidalShape.class, shape);

        // At t=0: sin(0) = 0, users = mid = 55
        LoadShape.ShapeTick tick = shape.tick(Duration.ofSeconds(0), 0);
        assertNotNull(tick);
        assertEquals(55, tick.targetUsers());

        // Past duration: null
        assertNull(shape.tick(Duration.ofSeconds(121), 55));
    }

    // =========================================================================
    // Duration parsing
    // =========================================================================

    @Test
    void parseDurationSeconds() {
        assertEquals(Duration.ofSeconds(30), ShapeParser.parseDuration("30s", "test"));
    }

    @Test
    void parseDurationMinutes() {
        assertEquals(Duration.ofMinutes(5), ShapeParser.parseDuration("5m", "test"));
    }

    @Test
    void parseDurationHours() {
        assertEquals(Duration.ofHours(2), ShapeParser.parseDuration("2h", "test"));
    }

    @Test
    void parseDurationBareNumber() {
        assertEquals(Duration.ofSeconds(45), ShapeParser.parseDuration("45", "test"));
    }

    // =========================================================================
    // Error cases
    // =========================================================================

    @Test
    void rejectsNullSpec() {
        assertThrows(NullPointerException.class, () -> ShapeParser.parse(null));
    }

    @Test
    void rejectsBlankSpec() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("   "));
    }

    @Test
    void rejectsNoTypePrefix() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("nocolon"));
    }

    @Test
    void rejectsUnknownType() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ShapeParser.parse("unknown:1:2:3"));
        assertTrue(ex.getMessage().contains("Unknown shape type"));
    }

    @Test
    void rejectsConstantWithWrongParamCount() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("constant:50"));
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("constant:50:60s:extra"));
    }

    @Test
    void rejectsRampWithWrongParamCount() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("ramp:0:100"));
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("ramp:0:100:60s:extra"));
    }

    @Test
    void rejectsStepWithWrongParamCount() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("step:10:15s:100"));
    }

    @Test
    void rejectsSineWithWrongParamCount() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("sine:10:100:30s"));
    }

    @Test
    void rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("constant:50:abc"));
    }

    @Test
    void rejectsInvalidUserCount() {
        assertThrows(IllegalArgumentException.class, () -> ShapeParser.parse("constant:abc:60s"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "stages:0:10:30s,10:50:60s,50:0:30s",
            "stages:0:100:60s",
            "stages:0:10:30s,10:10:5m"
    })
    void parsesVariousStagesFormats(String spec) {
        LoadShape shape = ShapeParser.parse(spec);
        assertInstanceOf(StagesShape.class, shape);
        // Should be able to tick at t=0 without error
        assertNotNull(shape.tick(Duration.ZERO, 0));
    }

    @Test
    void caseInsensitiveType() {
        // Type prefix should be case-insensitive
        LoadShape shape = ShapeParser.parse("CONSTANT:50:60s");
        assertInstanceOf(ConstantShape.class, shape);
    }

    @Test
    void whitespaceHandling() {
        // Leading/trailing whitespace on spec should be trimmed
        LoadShape shape = ShapeParser.parse("  constant:50:60s  ");
        assertInstanceOf(ConstantShape.class, shape);
    }
}
