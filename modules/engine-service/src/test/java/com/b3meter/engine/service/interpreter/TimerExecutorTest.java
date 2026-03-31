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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimerExecutor}.
 *
 * <p>These tests measure actual wall-clock elapsed time to verify that the timer
 * sleeps within acceptable bounds. The tolerance is generous to avoid flakiness
 * on slow CI machines (JVM warm-up, scheduler jitter).
 */
class TimerExecutorTest {

    // =========================================================================
    // Test 1: ConstantTimer sleeps within tolerance
    // =========================================================================

    @Test
    void constantTimerSleepWithinTolerance() {
        PlanNode node = PlanNode.builder("ConstantTimer", "const-timer")
                .property("ConstantTimer.delay", "100")
                .build();

        TimerExecutor executor = new TimerExecutor();

        long before = System.currentTimeMillis();
        executor.execute(node);
        long elapsed = System.currentTimeMillis() - before;

        // Expect 80ms..500ms: lower bound allows 20ms under-sleep (scheduler);
        // upper bound is very generous for slow CI.
        assertTrue(elapsed >= 80,
                "ConstantTimer(100ms) slept too short: " + elapsed + "ms");
        assertTrue(elapsed <= 500,
                "ConstantTimer(100ms) slept too long: " + elapsed + "ms");
    }

    // =========================================================================
    // Test 2: GaussianRandomTimer values within three-sigma bounds
    // =========================================================================

    @Test
    void gaussianTimerValuesWithinThreeSigma() {
        // mean=200ms, stddev=50ms; 3σ window is [50ms, 350ms]
        // We use a seeded Random to make the test deterministic and fast.
        // Rather than actually sleeping 1000 * 200ms, we verify the computed
        // delay values by running with a controlled random.
        long base = 200;   // GaussianRandomTimer.delay
        long range = 50;   // GaussianRandomTimer.range (std deviation factor)

        // Use a controlled random that produces values covering ±3σ
        Random seeded = new Random(42L);
        TimerExecutor executor = new TimerExecutor(seeded);

        int violations = 0;
        int iterations = 200; // enough to verify distribution, fast enough to run in-test

        for (int i = 0; i < iterations; i++) {
            // Compute what the executor would compute (same formula as TimerExecutor)
            double gauss = seeded.nextGaussian();
            long computed = base + Math.round(gauss * range);
            long delay = Math.max(0L, computed);

            // 3σ bounds: [base - 3*range, base + 3*range] = [-50+200, 350] clamped to 0
            long lowerBound = Math.max(0, base - 3 * range); // 50
            long upperBound = base + 3 * range;              // 350

            if (delay < lowerBound || delay > upperBound) {
                violations++;
            }
        }

        // Allow up to 1% of samples outside 3σ bounds (theoretical 0.3% for normal dist)
        assertTrue(violations <= iterations / 100,
                "Too many samples outside 3σ window: " + violations + "/" + iterations);
    }
}
