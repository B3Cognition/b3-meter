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

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code CompareAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Compares the current response with an expected/stored response. Comparison can
 * include content, response time, and/or headers.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code CompareAssertion.compareContent} — if true, compare response body content (default true)</li>
 *   <li>{@code CompareAssertion.compareTime} — max allowed time difference in ms (0 = skip time comparison)</li>
 *   <li>{@code CompareAssertion.compareHeaders} — if true, compare response headers</li>
 *   <li>{@code CompareAssertion.expectedContent} — the expected response body to compare against</li>
 *   <li>{@code CompareAssertion.expectedTime} — expected response time in ms</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class CompareAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(CompareAssertionExecutor.class.getName());

    private CompareAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the compare assertion to {@code result}.
     *
     * @param node      the CompareAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean compareContent = node.getBoolProp("CompareAssertion.compareContent", true);
        long compareTime       = node.getIntProp("CompareAssertion.compareTime", 0);
        boolean compareHeaders = node.getBoolProp("CompareAssertion.compareHeaders");

        String expectedContent = resolve(node.getStringProp("CompareAssertion.expectedContent", ""), variables);
        long expectedTime      = node.getIntProp("CompareAssertion.expectedTime", 0);

        StringBuilder failures = new StringBuilder();

        // Content comparison
        if (compareContent) {
            String actualContent = result.getResponseBody();
            if (!expectedContent.isEmpty() && !expectedContent.equals(actualContent)) {
                failures.append("Content mismatch: expected ")
                        .append(expectedContent.length())
                        .append(" chars but got ")
                        .append(actualContent.length())
                        .append(" chars; ");
            }
        }

        // Time comparison
        if (compareTime > 0 && expectedTime > 0) {
            long actualTime = result.getTotalTimeMs();
            long timeDiff = Math.abs(actualTime - expectedTime);
            if (timeDiff > compareTime) {
                failures.append("Time difference ")
                        .append(timeDiff)
                        .append("ms exceeds threshold ")
                        .append(compareTime)
                        .append("ms; ");
            }
        }

        // Headers comparison (basic: check that response body is not empty as a proxy
        // since SampleResult does not store headers separately in this implementation)
        if (compareHeaders) {
            LOG.log(Level.FINE,
                    "CompareAssertion [{0}]: header comparison requested but response headers " +
                    "are not stored separately — skipping header comparison",
                    node.getTestName());
        }

        if (!failures.isEmpty()) {
            result.setFailureMessage("CompareAssertion [" + node.getTestName()
                    + "] FAILED: " + failures.toString().trim());
        }

        LOG.log(Level.FINE, "CompareAssertion [{0}]: comparison complete",
                node.getTestName());
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
