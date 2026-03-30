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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code ConstantThroughputTimer} node.
 *
 * <p>The Constant Throughput Timer introduces variable delays to achieve a target
 * throughput expressed in samples per minute. JMeter properties:
 * <ul>
 *   <li>{@code throughput} — target samples per minute (double)</li>
 *   <li>{@code calcMode} — calculation mode:
 *       0 = this thread only, 1 = all active threads (simplified: both use per-thread tracking)</li>
 * </ul>
 *
 * <p>Algorithm: {@code delay = max(0, (60000 / throughput) - elapsed)}
 * where {@code elapsed} is the time since the last sample completed on this thread.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class ConstantThroughputTimerExecutor {

    private static final Logger LOG = Logger.getLogger(ConstantThroughputTimerExecutor.class.getName());

    /**
     * Thread-local tracking of the last execution timestamp so each VU thread
     * independently paces itself.
     */
    private static final ThreadLocal<Long> LAST_SAMPLE_TIME = ThreadLocal.withInitial(() -> 0L);

    private ConstantThroughputTimerExecutor() {}

    /**
     * Executes the constant throughput timer, sleeping the calling thread for
     * the calculated delay to achieve the target throughput.
     *
     * @param node the timer node; must not be {@code null}
     */
    public static void execute(PlanNode node) {
        Objects.requireNonNull(node, "node must not be null");

        double throughput = parseDouble(
                node.getStringProp("throughput", "0"), 0.0);
        if (throughput <= 0) {
            LOG.log(Level.FINE,
                    "ConstantThroughputTimer [{0}]: throughput <= 0, no delay",
                    node.getTestName());
            return;
        }

        long intervalMs = Math.round(60_000.0 / throughput);
        long now = System.currentTimeMillis();
        long lastTime = LAST_SAMPLE_TIME.get();

        long elapsed = (lastTime > 0) ? (now - lastTime) : intervalMs;
        long delayMs = Math.max(0, intervalMs - elapsed);

        if (delayMs > 0) {
            LOG.log(Level.FINE,
                    "ConstantThroughputTimer [{0}]: sleeping {1} ms (target interval {2} ms)",
                    new Object[]{node.getTestName(), delayMs, intervalMs});
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LAST_SAMPLE_TIME.set(System.currentTimeMillis());
    }

    /**
     * Computes the delay in milliseconds for the given throughput and elapsed time.
     * Visible for testing.
     *
     * @param throughputPerMinute target samples per minute
     * @param elapsedSinceLastMs time since last sample in ms
     * @return delay in ms (non-negative)
     */
    static long computeDelay(double throughputPerMinute, long elapsedSinceLastMs) {
        if (throughputPerMinute <= 0) return 0;
        long intervalMs = Math.round(60_000.0 / throughputPerMinute);
        return Math.max(0, intervalMs - elapsedSinceLastMs);
    }

    /** Resets the thread-local last sample time. Useful for testing. */
    static void resetThreadLocal() {
        LAST_SAMPLE_TIME.remove();
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
