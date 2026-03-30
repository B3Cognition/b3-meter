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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code InterleaveControl} {@link PlanNode}.
 *
 * <p>Executes children in round-robin order: one child per iteration,
 * cycling through children sequentially. Counter can be per-VU (default)
 * or shared across all VUs.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code InterleaveControl.style} — 0 = simple interleave (default)</li>
 *   <li>{@code InterleaveControl.accrossThreads} — share counter across VUs
 *       (note: typo is intentional, matching JMeter source)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class InterleaveControllerExecutor {

    private static final Logger LOG = Logger.getLogger(InterleaveControllerExecutor.class.getName());

    /** Per-VU tracking variable prefix. */
    private static final String COUNTER_PREFIX = "__jmn_interleave_";

    /** Shared counters for accrossThreads mode. Key: controller testName. */
    private static final ConcurrentHashMap<String, AtomicInteger> SHARED_COUNTERS =
            new ConcurrentHashMap<>();

    private final NodeInterpreter interpreter;

    /**
     * Constructs an {@code InterleaveControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public InterleaveControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes the next child in round-robin order.
     *
     * @param node      the InterleaveControl node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from the selected child
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<PlanNode> children = node.getChildren();
        if (children.isEmpty()) {
            LOG.log(Level.FINE,
                    "InterleaveControllerExecutor [{0}]: no children — skipping",
                    node.getTestName());
            return List.of();
        }

        boolean accrossThreads = node.getBoolProp("InterleaveControl.accrossThreads", false);

        int index;
        if (accrossThreads) {
            AtomicInteger counter = SHARED_COUNTERS.computeIfAbsent(
                    node.getTestName(), k -> new AtomicInteger(0));
            index = counter.getAndIncrement() % children.size();
        } else {
            // Per-VU counter stored in variable map
            String key = COUNTER_PREFIX + node.getTestName();
            String currentStr = variables.get(key);
            int current = 0;
            if (currentStr != null) {
                try {
                    current = Integer.parseInt(currentStr);
                } catch (NumberFormatException ignored) {
                    // start at 0
                }
            }
            index = current % children.size();
            variables.put(key, String.valueOf(current + 1));
        }

        LOG.log(Level.FINE,
                "InterleaveControllerExecutor [{0}]: executing child at index {1} of {2}",
                new Object[]{node.getTestName(), index, children.size()});

        return interpreter.executeChildren(List.of(children.get(index)), variables);
    }

    /**
     * Resets all shared counters. Intended for testing only.
     */
    static void resetCounters() {
        SHARED_COUNTERS.clear();
    }
}
