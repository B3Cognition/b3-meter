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
 * Executes timer {@link PlanNode}s: {@code ConstantTimer} and {@code GaussianRandomTimer}.
 *
 * <p>Timers introduce deliberate think-time delays between samplers to model realistic
 * user behaviour. JMeter's timer model is:
 * <ul>
 *   <li>{@code ConstantTimer} — sleep exactly {@code ConstantTimer.delay} milliseconds.</li>
 *   <li>{@code GaussianRandomTimer} — sleep
 *       {@code GaussianRandomTimer.delay + gaussian × GaussianRandomTimer.range} ms,
 *       where {@code gaussian} is a sample from the standard normal distribution.</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class TimerExecutor {

    private static final Logger LOG = Logger.getLogger(TimerExecutor.class.getName());

    /** Per-instance Random for Gaussian sampling. Not shared across VUs intentionally. */
    private final Random random;

    /**
     * Constructs a {@code TimerExecutor} with a fresh {@link Random} instance.
     */
    public TimerExecutor() {
        this.random = new Random();
    }

    /**
     * Constructs a {@code TimerExecutor} with the supplied {@link Random}
     * (useful for deterministic testing).
     *
     * @param random the random source; must not be {@code null}
     */
    public TimerExecutor(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Returns the {@link Random} instance used by this executor.
     * Visible for use by other timer executors (e.g. PoissonRandomTimer).
     *
     * @return the random source; never {@code null}
     */
    public Random getRandom() {
        return random;
    }

    /**
     * Executes the timer described by {@code node}, blocking the calling thread for
     * the computed delay.
     *
     * <p>If the thread is interrupted during sleep, the interrupt flag is restored and
     * the method returns immediately.
     *
     * @param node the timer node ({@code ConstantTimer} or {@code GaussianRandomTimer});
     *             must not be {@code null}
     */
    public void execute(PlanNode node) {
        Objects.requireNonNull(node, "node must not be null");

        long delayMs = computeDelay(node);
        if (delayMs <= 0) {
            return;
        }

        LOG.log(Level.FINE, "TimerExecutor [{0}]: sleeping {1} ms",
                new Object[]{node.getTestName(), delayMs});
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private long computeDelay(PlanNode node) {
        String testClass = node.getTestClass();
        return switch (testClass) {
            case "ConstantTimer" -> {
                // ConstantTimer.delay is stored as a string in JMeter (template-capable)
                String raw = node.getStringProp("ConstantTimer.delay", "0");
                yield parseLong(raw, 0L);
            }
            case "GaussianRandomTimer" -> {
                long base     = parseLong(node.getStringProp("GaussianRandomTimer.delay", "0"), 0L);
                long range    = parseLong(node.getStringProp("GaussianRandomTimer.range", "0"), 0L);
                double gauss  = random.nextGaussian();
                long computed = base + Math.round(gauss * range);
                yield Math.max(0L, computed);
            }
            case "UniformRandomTimer" -> {
                long base  = parseLong(node.getStringProp("RandomTimer.delay",  "0"), 0L);
                long range = parseLong(node.getStringProp("RandomTimer.range",  "0"), 0L);
                yield base + (range > 0 ? Math.abs(random.nextLong()) % range : 0L);
            }
            default -> {
                LOG.log(Level.FINE, "TimerExecutor: unknown timer class [{0}] — no delay",
                        testClass);
                yield 0L;
            }
        };
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
