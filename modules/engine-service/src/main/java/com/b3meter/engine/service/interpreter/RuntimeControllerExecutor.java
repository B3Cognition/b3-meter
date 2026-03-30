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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code RuntimeController} {@link PlanNode}.
 *
 * <p>The Runtime Controller executes its children repeatedly for a specified
 * duration. JMeter property:
 * <ul>
 *   <li>{@code RuntimeController.seconds} — maximum runtime in seconds</li>
 * </ul>
 *
 * <p>Children are looped until the specified duration expires. If no duration
 * is set (or is {@code 0}), children are executed exactly once.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RuntimeControllerExecutor {

    private static final Logger LOG = Logger.getLogger(RuntimeControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code RuntimeControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public RuntimeControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes children repeatedly until the configured duration expires.
     *
     * @param node      the RuntimeController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of all {@link SampleResult}s produced during the runtime period
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        long seconds = parseLong(node.getStringProp("RuntimeController.seconds", "0"), 0L);
        if (seconds <= 0) {
            LOG.log(Level.FINE,
                    "RuntimeControllerExecutor [{0}]: no duration set — executing children once",
                    node.getTestName());
            return interpreter.executeChildren(node.getChildren(), variables);
        }

        long durationMs = seconds * 1000L;
        long startTime = System.currentTimeMillis();
        List<SampleResult> allResults = new ArrayList<>();

        LOG.log(Level.FINE,
                "RuntimeControllerExecutor [{0}]: running for {1} seconds",
                new Object[]{node.getTestName(), seconds});

        while (!Thread.currentThread().isInterrupted()) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= durationMs) break;

            List<SampleResult> iterResults = interpreter.executeChildren(node.getChildren(), variables);
            allResults.addAll(iterResults);
        }

        LOG.log(Level.FINE,
                "RuntimeControllerExecutor [{0}]: completed with {1} samples",
                new Object[]{node.getTestName(), allResults.size()});

        return allResults;
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
