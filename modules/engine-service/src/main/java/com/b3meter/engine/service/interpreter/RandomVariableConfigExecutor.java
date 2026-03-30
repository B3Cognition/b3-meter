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

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code RandomVariableConfig} {@link PlanNode} to generate random values.
 *
 * <p>Generates a random number within a specified range and stores it in a
 * JMeter variable. Supports per-thread and shared random sequences,
 * optional seeding for reproducibility, and output formatting.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code variableName} — variable name to store the result</li>
 *   <li>{@code minimumValue} — minimum value (inclusive, default "1")</li>
 *   <li>{@code maximumValue} — maximum value (inclusive, default "100")</li>
 *   <li>{@code outputFormat} — DecimalFormat pattern (empty = plain number)</li>
 *   <li>{@code perThread} — each VU gets independent random sequence</li>
 *   <li>{@code randomSeed} — seed for reproducible sequences</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RandomVariableConfigExecutor {

    private static final Logger LOG = Logger.getLogger(RandomVariableConfigExecutor.class.getName());

    /**
     * Per-thread seeded Random instances for reproducible sequences.
     * Key: variable name, Value: seeded Random.
     */
    private static final ThreadLocal<ConcurrentHashMap<String, Random>> THREAD_RANDOMS =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Global seeded Random instances for shared mode.
     */
    private static final ConcurrentHashMap<String, Random> GLOBAL_RANDOMS =
            new ConcurrentHashMap<>();

    private RandomVariableConfigExecutor() {}

    /**
     * Generates a random value and stores it in the VU variable map.
     *
     * @param node      the RandomVariableConfig node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String varName = node.getStringProp("variableName", "");
        String minStr = node.getStringProp("minimumValue", "1");
        String maxStr = node.getStringProp("maximumValue", "100");
        String outputFormat = node.getStringProp("outputFormat", "");
        boolean perThread = node.getBoolProp("perThread", true);
        String seedStr = node.getStringProp("randomSeed", "");

        if (varName.isEmpty()) {
            LOG.log(Level.FINE,
                    "RandomVariableConfigExecutor [{0}]: no variable name — skipping",
                    node.getTestName());
            return;
        }

        long min = parseLong(minStr, 1L);
        long max = parseLong(maxStr, 100L);

        if (min > max) {
            long tmp = min;
            min = max;
            max = tmp;
        }

        long value = generateRandom(varName, min, max, seedStr, perThread);

        String formatted;
        if (outputFormat != null && !outputFormat.isEmpty()) {
            try {
                formatted = new DecimalFormat(outputFormat).format(value);
            } catch (IllegalArgumentException e) {
                formatted = String.valueOf(value);
            }
        } else {
            formatted = String.valueOf(value);
        }

        variables.put(varName, formatted);

        LOG.log(Level.FINE,
                "RandomVariableConfigExecutor [{0}]: {1} = {2}",
                new Object[]{node.getTestName(), varName, formatted});
    }

    /**
     * Generates a random long value in the range [min, max] (inclusive).
     */
    private static long generateRandom(String varName, long min, long max,
                                         String seedStr, boolean perThread) {
        // Range is [min, max] inclusive, so bound = max - min + 1
        long range = max - min + 1;

        if (seedStr != null && !seedStr.isEmpty()) {
            // Use seeded Random for reproducible sequences
            long seed;
            try {
                seed = Long.parseLong(seedStr.trim());
            } catch (NumberFormatException e) {
                seed = seedStr.hashCode();
            }

            Random rng;
            if (perThread) {
                final long finalSeed = seed;
                rng = THREAD_RANDOMS.get()
                        .computeIfAbsent(varName, k -> new Random(finalSeed));
            } else {
                final long finalSeed = seed;
                rng = GLOBAL_RANDOMS.computeIfAbsent(varName, k -> new Random(finalSeed));
            }

            synchronized (rng) {
                if (range <= 0) return min; // overflow protection
                return min + (Math.abs(rng.nextLong()) % range);
            }
        }

        // No seed — use ThreadLocalRandom
        if (range <= 0) return min; // overflow protection
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static long parseLong(String s, long defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Resets all random instances. Intended for testing only.
     */
    static void resetAll() {
        GLOBAL_RANDOMS.clear();
        THREAD_RANDOMS.remove();
    }
}
