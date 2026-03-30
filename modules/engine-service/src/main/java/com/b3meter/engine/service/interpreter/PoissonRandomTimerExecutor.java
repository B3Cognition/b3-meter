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

import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code PoissonRandomTimer} node.
 *
 * <p>The Poisson Random Timer produces delays following an exponential distribution
 * (inter-arrival times of a Poisson process) plus a constant offset. JMeter properties:
 * <ul>
 *   <li>{@code lambda} — average delay in ms (the lambda parameter)</li>
 *   <li>{@code constantDelay} — constant offset added to the random delay</li>
 * </ul>
 *
 * <p>Delay formula: {@code -lambda * ln(1 - U) + constantDelay}
 * where U is a uniform random variable in [0, 1).
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class PoissonRandomTimerExecutor {

    private static final Logger LOG = Logger.getLogger(PoissonRandomTimerExecutor.class.getName());

    private PoissonRandomTimerExecutor() {}

    /**
     * Executes the Poisson random timer, sleeping the calling thread for the computed delay.
     *
     * @param node   the timer node; must not be {@code null}
     * @param random random source for the exponential variate
     */
    public static void execute(PlanNode node, Random random) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(random, "random must not be null");

        long delayMs = computeDelay(node, random);
        if (delayMs <= 0) return;

        LOG.log(Level.FINE,
                "PoissonRandomTimer [{0}]: sleeping {1} ms",
                new Object[]{node.getTestName(), delayMs});
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Computes the Poisson-distributed delay.
     *
     * @param node   the timer node
     * @param random random source
     * @return delay in ms (non-negative)
     */
    static long computeDelay(PlanNode node, Random random) {
        double lambda = parseDouble(node.getStringProp("lambda", "0"), 0.0);
        long constantDelay = parseLong(node.getStringProp("constantDelay", "0"), 0L);

        if (lambda <= 0) {
            return Math.max(0, constantDelay);
        }

        // Exponential variate: -lambda * ln(1 - U)
        double u = random.nextDouble();
        // Guard against u == 1.0 (would give -ln(0) = infinity)
        if (u >= 1.0) u = 0.9999999;
        double exponentialDelay = -lambda * Math.log(1.0 - u);

        return Math.max(0, Math.round(exponentialDelay) + constantDelay);
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
