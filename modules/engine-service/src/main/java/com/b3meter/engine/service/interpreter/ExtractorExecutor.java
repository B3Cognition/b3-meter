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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes extractor {@link PlanNode}s: {@code RegexExtractor} and {@code JSONPostProcessor}
 * (also exposed as {@code JSONPathExtractor}).
 *
 * <p>Extractors read a field from the last {@link SampleResult} and store the extracted
 * value into the VU variable map for use by downstream samplers, controllers, and
 * assertions.
 *
 * <h2>RegexExtractor</h2>
 * Properties:
 * <ul>
 *   <li>{@code RegexExtractor.refname} — variable name to set</li>
 *   <li>{@code RegexExtractor.regex} — Java regular expression</li>
 *   <li>{@code RegexExtractor.template} — capture group template, e.g. {@code $1$}</li>
 *   <li>{@code RegexExtractor.match_number} — which match to use (1-based; -1 = random)</li>
 *   <li>{@code RegexExtractor.default} — default value when nothing matches</li>
 * </ul>
 *
 * <h2>JSONPostProcessor / JSONPathExtractor</h2>
 * Properties:
 * <ul>
 *   <li>{@code JSONPathExtractor.referenceNames} — variable name to set</li>
 *   <li>{@code JSONPathExtractor.jsonPathExprs} — simple JSONPath expression (dot-notation or bracket-notation)</li>
 *   <li>{@code JSONPathExtractor.defaultValues} — default value when nothing matches</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class ExtractorExecutor {

    private static final Logger LOG = Logger.getLogger(ExtractorExecutor.class.getName());

    private ExtractorExecutor() {
        // utility class — not instantiable
    }

    /**
     * Applies the extractor described by {@code node} to {@code result}, storing
     * the extracted value into {@code variables}.
     *
     * @param node      the extractor node; must not be {@code null}
     * @param result    the most recent sample result to extract from; must not be {@code null}
     * @param variables mutable VU variable map; the extracted value is stored here
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        switch (node.getTestClass()) {
            case "RegexExtractor"    -> executeRegex(node, result, variables);
            case "JSONPostProcessor",
                 "JSONPathExtractor" -> executeJsonPath(node, result, variables);
            default -> LOG.log(Level.FINE,
                    "ExtractorExecutor: unknown extractor class [{0}] — skipping",
                    node.getTestClass());
        }
    }

    // -------------------------------------------------------------------------
    // RegexExtractor
    // -------------------------------------------------------------------------

    private static void executeRegex(PlanNode node, SampleResult result,
                                      Map<String, String> variables) {
        String refName     = node.getStringProp("RegexExtractor.refname");
        String regex       = node.getStringProp("RegexExtractor.regex");
        String template    = node.getStringProp("RegexExtractor.template",  "$1$");
        String defaultVal  = node.getStringProp("RegexExtractor.default",   "");
        int    matchNumber = node.getIntProp("RegexExtractor.match_number",  1);

        if (refName == null || refName.isBlank() || regex == null || regex.isBlank()) {
            LOG.log(Level.WARNING, "RegexExtractor [{0}]: missing refname or regex — skipping",
                    node.getTestName());
            return;
        }

        String body = result.getResponseBody();
        String extracted = defaultVal;

        try {
            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(body);

            if (matchNumber == -1) {
                // Random match: collect all matches and pick one
                java.util.List<String> matches = new java.util.ArrayList<>();
                while (matcher.find()) {
                    matches.add(applyTemplate(template, matcher));
                }
                if (!matches.isEmpty()) {
                    int idx = (int) (Math.random() * matches.size());
                    extracted = matches.get(idx);
                }
            } else {
                // Find the Nth match (matchNumber is 1-based)
                int target = Math.max(1, matchNumber);
                int found = 0;
                while (matcher.find()) {
                    found++;
                    if (found == target) {
                        extracted = applyTemplate(template, matcher);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "RegexExtractor [" + node.getTestName() + "]: error — " + e.getMessage(), e);
        }

        variables.put(refName, extracted);
        LOG.log(Level.FINE, "RegexExtractor [{0}]: {1} = {2}",
                new Object[]{node.getTestName(), refName, extracted});
    }

    /**
     * Applies a JMeter capture-group template (e.g. {@code $1$}, {@code $2$}) to
     * a successful {@link Matcher}.
     */
    private static String applyTemplate(String template, Matcher matcher) {
        if (template == null || template.isBlank()) {
            return matcher.group(0);
        }
        // Replace $N$ with capture group N
        String result = template;
        int groupCount = matcher.groupCount();
        for (int g = groupCount; g >= 1; g--) {
            String groupValue = matcher.group(g);
            result = result.replace("$" + g + "$", groupValue != null ? groupValue : "");
        }
        // $0$ = whole match
        result = result.replace("$0$", matcher.group(0));
        return result;
    }

    // -------------------------------------------------------------------------
    // JSONPathExtractor / JSONPostProcessor
    // -------------------------------------------------------------------------

    private static void executeJsonPath(PlanNode node, SampleResult result,
                                         Map<String, String> variables) {
        // Support both JSONPostProcessor (JMX standard) and JSONPathExtractor (legacy)
        String refName    = node.getStringProp("JSONPostProcessor.referenceNames");
        if (refName == null) refName = node.getStringProp("JSONPathExtractor.referenceNames");
        String jsonPath   = node.getStringProp("JSONPostProcessor.jsonPathExprs");
        if (jsonPath == null) jsonPath = node.getStringProp("JSONPathExtractor.jsonPathExprs");
        String defaultVal = node.getStringProp("JSONPostProcessor.defaultValues",
                            node.getStringProp("JSONPathExtractor.defaultValues", ""));

        if (refName == null || refName.isBlank() || jsonPath == null || jsonPath.isBlank()) {
            LOG.log(Level.WARNING, "JSONPathExtractor [{0}]: missing referenceNames or jsonPathExprs — skipping",
                    node.getTestName());
            return;
        }

        String body = result.getResponseBody();
        String extracted = defaultVal;

        try {
            extracted = extractJsonPath(body, jsonPath, defaultVal);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "JSONPathExtractor [" + node.getTestName() + "]: error — " + e.getMessage(), e);
        }

        variables.put(refName, extracted);
        LOG.log(Level.FINE, "JSONPathExtractor [{0}]: {1} = {2}",
                new Object[]{node.getTestName(), refName, extracted});
    }

    /**
     * Minimal JSONPath evaluator supporting {@code $.key}, {@code $.key.subkey},
     * {@code $[0]}, and {@code $.key[0].subkey} patterns.
     *
     * <p>This is a deliberately simple implementation that covers the common cases used
     * in JMeter test plans without introducing an external JSON library. Full JSONPath
     * (filters, wildcards, recursive descent) is out of scope for the engine-service
     * layer which must remain framework-free.
     */
    static String extractJsonPath(String json, String jsonPath, String defaultValue) {
        if (json == null || json.isBlank()) return defaultValue;
        if (jsonPath == null || jsonPath.isBlank()) return defaultValue;

        // Normalise: strip leading "$" or "$."
        String path = jsonPath.trim();
        if (path.equals("$")) {
            return json.trim();
        }
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("$[")) {
            path = path.substring(1);
        } else if (path.startsWith("$")) {
            path = path.substring(1);
        }

        // Tokenise path into keys/indices
        String[] tokens = path.split("\\.");
        String current = json.trim();

        for (String token : tokens) {
            if (token.isBlank()) continue;

            // Handle array index: key[N] or [N]
            if (token.contains("[")) {
                int bracketOpen  = token.indexOf('[');
                int bracketClose = token.indexOf(']');
                String key = token.substring(0, bracketOpen);
                int index  = Integer.parseInt(token.substring(bracketOpen + 1, bracketClose).trim());

                if (!key.isBlank()) {
                    current = findJsonValue(current, key);
                    if (current == null) return defaultValue;
                }
                current = getJsonArrayElement(current, index);
                if (current == null) return defaultValue;
            } else {
                current = findJsonValue(current, token);
                if (current == null) return defaultValue;
            }
        }

        return stripQuotes(current.trim());
    }

    /**
     * Finds the value of {@code key} in a simple JSON object string.
     * Returns the raw value substring (may be quoted string, number, object, array).
     */
    private static String findJsonValue(String json, String key) {
        // Match "key": value where value can be string, number, boolean, null, {}, []
        String quotedKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(quotedKey);
        if (keyIdx < 0) return null;

        int colon = json.indexOf(':', keyIdx + quotedKey.length());
        if (colon < 0) return null;

        int valueStart = colon + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        return extractValue(json, valueStart);
    }

    /** Returns the Nth element (0-based) from a JSON array string. */
    private static String getJsonArrayElement(String json, int index) {
        int start = json.indexOf('[');
        if (start < 0) return null;
        start++; // skip '['

        int depth = 0;
        int elementStart = start;
        int elementCount = 0;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (depth == 0) {
                    // End of array
                    if (elementCount == index) {
                        return json.substring(elementStart, i).trim();
                    }
                    return null;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                if (elementCount == index) {
                    return json.substring(elementStart, i).trim();
                }
                elementCount++;
                elementStart = i + 1;
            }
        }
        return null;
    }

    /**
     * Extracts a complete JSON value starting at {@code start} (string, number,
     * boolean, null, object, or array).
     */
    private static String extractValue(String json, int start) {
        char first = json.charAt(start);
        if (first == '"') {
            // String value
            int end = start + 1;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '\\') {
                    end += 2; // skip escaped character
                } else if (c == '"') {
                    return json.substring(start, end + 1);
                } else {
                    end++;
                }
            }
            return null;
        } else if (first == '{' || first == '[') {
            // Object or array — find matching close bracket
            char open  = first;
            char close = (first == '{') ? '}' : ']';
            int depth = 1;
            int end   = start + 1;
            while (end < json.length() && depth > 0) {
                char c = json.charAt(end);
                if (c == open)  depth++;
                else if (c == close) depth--;
                end++;
            }
            return json.substring(start, end);
        } else {
            // Number, boolean, null — read until delimiter
            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
                end++;
            }
            return json.substring(start, end);
        }
    }

    /** Strips surrounding double-quotes from a JSON string value. */
    private static String stripQuotes(String value) {
        if (value != null && value.length() >= 2
                && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
