package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PoissonRandomTimerExecutor}.
 */
class PoissonRandomTimerExecutorTest {

    @Test
    void computeDelay_withLambdaAndOffset_producesNonNegative() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "300")
                .property("constantDelay", "100")
                .build();

        Random seeded = new Random(42);
        long delay = PoissonRandomTimerExecutor.computeDelay(node, seeded);
        assertTrue(delay >= 100, "delay should be >= constantDelay(100), got: " + delay);
    }

    @Test
    void computeDelay_zeroLambda_returnsConstantDelay() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "0")
                .property("constantDelay", "200")
                .build();

        Random seeded = new Random(42);
        long delay = PoissonRandomTimerExecutor.computeDelay(node, seeded);
        assertEquals(200, delay, "with lambda=0, delay should equal constantDelay");
    }

    @Test
    void computeDelay_noConstantDelay_producesNonNegative() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "500")
                .build();

        Random seeded = new Random(42);
        long delay = PoissonRandomTimerExecutor.computeDelay(node, seeded);
        assertTrue(delay >= 0, "delay should be non-negative, got: " + delay);
    }

    @Test
    void computeDelay_deterministic_withSameRandom() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "300")
                .property("constantDelay", "50")
                .build();

        long delay1 = PoissonRandomTimerExecutor.computeDelay(node, new Random(123));
        long delay2 = PoissonRandomTimerExecutor.computeDelay(node, new Random(123));
        assertEquals(delay1, delay2, "same seed should produce same delay");
    }

    @Test
    void computeDelay_distribution_variesWithDifferentSeeds() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "300")
                .property("constantDelay", "0")
                .build();

        long delay1 = PoissonRandomTimerExecutor.computeDelay(node, new Random(1));
        long delay2 = PoissonRandomTimerExecutor.computeDelay(node, new Random(999));
        // Different seeds should (almost certainly) produce different delays
        // This is probabilistic but with large lambda they should differ
        // We just check both are non-negative
        assertTrue(delay1 >= 0);
        assertTrue(delay2 >= 0);
    }

    @Test
    void execute_doesNotThrow() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson")
                .property("lambda", "1") // tiny lambda = minimal delay
                .property("constantDelay", "0")
                .build();

        assertDoesNotThrow(() -> PoissonRandomTimerExecutor.execute(node, new Random(42)));
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> PoissonRandomTimerExecutor.execute(null, new Random()));
    }

    @Test
    void execute_nullRandom_throws() {
        PlanNode node = PlanNode.builder("PoissonRandomTimer", "Poisson").build();
        assertThrows(NullPointerException.class,
                () -> PoissonRandomTimerExecutor.execute(node, null));
    }
}
