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

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${varName}} variable references in template strings.
 *
 * <p>Supports:
 * <ul>
 *   <li>Simple substitution: {@code ${varName}} → resolved value from the variable map.</li>
 *   <li>Built-in function {@code ${__property(key)}} → resolved via the same variable map
 *       (properties are stored as variables with the same key).</li>
 *   <li>Missing variables are left as-is (the literal {@code ${varName}} string is preserved)
 *       so downstream components can detect unresolved references.</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class VariableResolver {

    /** Pattern matching {@code ${...}} references (non-greedy inner match). */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /** Pattern matching the {@code __property(key)} built-in function call. */
    private static final Pattern PROPERTY_FN = Pattern.compile("__property\\(([^)]+)\\)");

    private VariableResolver() {
        // utility class — not instantiable
    }

    /**
     * Replaces all {@code ${varName}} references in {@code template} with values from
     * {@code variables}. References whose names are not found in the map are left unchanged.
     *
     * @param template  the string possibly containing variable references; must not be {@code null}
     * @param variables map of variable names to their current values; must not be {@code null}
     * @return the resolved string; never {@code null}
     * @throws NullPointerException if {@code template} or {@code variables} is {@code null}
     */
    public static String resolve(String template, Map<String, String> variables) {
        Objects.requireNonNull(template,  "template must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        if (!template.contains("${")) {
            // Fast path — no references at all
            return template;
        }

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 32);

        while (matcher.find()) {
            String inner = matcher.group(1); // everything between ${ and }
            String resolved = resolveInner(inner, variables);
            // appendReplacement requires the replacement to have backslashes/$ escaped
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Resolves a single inner expression (the content between {@code ${} and {@code }}).
     *
     * <p>Handles the {@code __property(key)} built-in. All other expressions are treated
     * as plain variable names. Unknown variables are returned as the original
     * {@code ${inner}} literal.
     */
    private static String resolveInner(String inner, Map<String, String> variables) {
        // Check for __property(key) built-in function
        Matcher fn = PROPERTY_FN.matcher(inner.trim());
        if (fn.matches()) {
            String key = fn.group(1).trim();
            String value = variables.get(key);
            return (value != null) ? value : "${" + inner + "}";
        }

        // Plain variable name
        String name = inner.trim();
        String value = variables.get(name);
        return (value != null) ? value : "${" + inner + "}";
    }
}
