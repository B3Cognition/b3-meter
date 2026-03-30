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
 * Executes an {@code IfController} {@link PlanNode}.
 *
 * <p>Reads the standard JMeter property:
 * <ul>
 *   <li>{@code IfController.condition} — condition expression string.</li>
 *   <li>{@code IfController.evaluateAll} — if {@code true}, evaluate on each iteration
 *       (default {@code false} means evaluate once; for the interpreter, we always evaluate).</li>
 * </ul>
 *
 * <h2>Supported condition syntax</h2>
 * Variable substitution is applied first (${varName} → resolved value), then the
 * resolved string is evaluated as a simple expression. Supported operators:
 * <ul>
 *   <li>{@code lhs == rhs} — string equality</li>
 *   <li>{@code lhs != rhs} — string inequality</li>
 *   <li>{@code lhs > rhs} — numeric greater-than</li>
 *   <li>{@code lhs < rhs} — numeric less-than</li>
 *   <li>{@code lhs >= rhs} — numeric greater-than-or-equal</li>
 *   <li>{@code lhs <= rhs} — numeric less-than-or-equal</li>
 *   <li>{@code lhs contains rhs} — string contains (case-sensitive)</li>
 *   <li>Bare {@code "true"} / {@code "false"} string literals.</li>
 * </ul>
 *
 * <p>Evaluation errors are logged and treated as {@code false} (children are skipped).
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class IfControllerExecutor {

    private static final Logger LOG = Logger.getLogger(IfControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs an {@code IfControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public IfControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Evaluates the condition for {@code node} and, if {@code true}, executes its children.
     *
     * @param node      the IfController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from children if condition was true; empty list otherwise
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String conditionTemplate = node.getStringProp("IfController.condition", "false");
        String condition = VariableResolver.resolve(conditionTemplate, variables);

        boolean result = evaluate(condition, node.getTestName());

        LOG.log(Level.FINE, "IfControllerExecutor [{0}]: condition ''{1}'' → {2}",
                new Object[]{node.getTestName(), condition, result});

        if (result) {
            return interpreter.executeChildren(node.getChildren(), variables);
        }
        return new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Expression evaluator
    // -------------------------------------------------------------------------

    /**
     * Evaluates a simple condition expression after variable substitution.
     *
     * @param condition the resolved condition string
     * @param label     controller label (for logging only)
     * @return {@code true} if the condition holds, {@code false} otherwise
     */
    static boolean evaluate(String condition, String label) {
        if (condition == null || condition.isBlank()) return false;
        String trimmed = condition.trim();

        // Boolean literals
        if ("true".equalsIgnoreCase(trimmed))  return true;
        if ("false".equalsIgnoreCase(trimmed)) return false;

        // Try binary operators (order matters: check >= before >)
        if (trimmed.contains(">=")) return compareNumeric(trimmed, ">=", label);
        if (trimmed.contains("<=")) return compareNumeric(trimmed, "<=", label);
        if (trimmed.contains("!=")) return compareStrings(trimmed, "!=");
        if (trimmed.contains("==")) return compareStrings(trimmed, "==");
        if (trimmed.contains(" > ")) return compareNumeric(trimmed, " > ", label);
        if (trimmed.contains(" < ")) return compareNumeric(trimmed, " < ", label);
        if (trimmed.toLowerCase().contains(" contains ")) {
            int idx = trimmed.toLowerCase().indexOf(" contains ");
            String lhs = trimmed.substring(0, idx).trim();
            String rhs = trimmed.substring(idx + 10).trim();
            return stripQuotes(lhs).contains(stripQuotes(rhs));
        }

        // Non-empty, non-"false" string → truthy (mirrors JMeter's groovy evaluator default)
        LOG.log(Level.FINE, "IfControllerExecutor [{0}]: treating ''{1}'' as truthy string literal",
                new Object[]{label, trimmed});
        return !trimmed.equalsIgnoreCase("false") && !trimmed.isBlank();
    }

    private static boolean compareStrings(String expr, String op) {
        String[] parts = expr.split(java.util.regex.Pattern.quote(op), 2);
        if (parts.length != 2) return false;
        String lhs = stripQuotes(parts[0].trim());
        String rhs = stripQuotes(parts[1].trim());
        return "==".equals(op) ? lhs.equals(rhs) : !lhs.equals(rhs);
    }

    private static boolean compareNumeric(String expr, String op, String label) {
        String[] parts = expr.split(java.util.regex.Pattern.quote(op.trim()), 2);
        if (parts.length != 2) return false;
        try {
            double lhs = Double.parseDouble(stripQuotes(parts[0].trim()));
            double rhs = Double.parseDouble(stripQuotes(parts[1].trim()));
            return switch (op.trim()) {
                case ">"  -> lhs > rhs;
                case "<"  -> lhs < rhs;
                case ">=" -> lhs >= rhs;
                case "<=" -> lhs <= rhs;
                default   -> false;
            };
        } catch (NumberFormatException e) {
            LOG.log(Level.FINE, "IfControllerExecutor [{0}]: numeric parse failed for ''{1}''",
                    new Object[]{label, expr});
            return false;
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                    || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
