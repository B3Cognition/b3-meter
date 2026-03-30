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
 * Executes an {@code HTTPURLRewritingModifier} {@link PlanNode} as a pre-processor.
 *
 * <p>Extracts a session ID from the previous response URL or body and stores it
 * as a variable so that subsequent request URLs can be rewritten to include it.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code argument} — the session parameter name to search for (e.g. "jsessionid", "PHPSESSID")</li>
 *   <li>{@code path_extension} — if true, append as path extension (;name=value) instead of query param</li>
 *   <li>{@code path_extension_no_equals} — if true, omit the equals sign (;namevalue)</li>
 *   <li>{@code path_extension_no_questionmark} — if true, no ? separator when adding as query param</li>
 * </ul>
 *
 * <p>Common session ID parameter names scanned:
 * jsessionid, PHPSESSID, sid, ASPSESSIONID, sessionid
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class URLRewritingModifierExecutor {

    private static final Logger LOG = Logger.getLogger(URLRewritingModifierExecutor.class.getName());

    private URLRewritingModifierExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Extracts session ID from the last response and stores it as a variable.
     *
     * @param node       the HTTPURLRewritingModifier node; must not be {@code null}
     * @param lastResult the most recent sample result to scan; may be {@code null} (no-op)
     * @param variables  current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult lastResult, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        if (lastResult == null) {
            LOG.log(Level.FINE,
                    "URLRewritingModifier [{0}]: no previous result — skipping",
                    node.getTestName());
            return;
        }

        String argument = node.getStringProp("argument", "");
        if (argument.isBlank()) {
            LOG.log(Level.WARNING,
                    "URLRewritingModifier [{0}]: no 'argument' property set — skipping",
                    node.getTestName());
            return;
        }

        boolean pathExtension          = node.getBoolProp("path_extension");
        boolean pathExtensionNoEquals  = node.getBoolProp("path_extension_no_equals");
        boolean pathExtensionNoQMark   = node.getBoolProp("path_extension_no_questionmark");

        String body = lastResult.getResponseBody();
        if (body == null || body.isBlank()) {
            LOG.log(Level.FINE,
                    "URLRewritingModifier [{0}]: empty response body — skipping",
                    node.getTestName());
            return;
        }

        // Try to find the session ID value in the response body
        // Pattern 1: name=value in query string or path (e.g., jsessionid=ABC123)
        String sessionId = extractSessionId(body, argument);

        if (sessionId != null) {
            variables.put(argument, sessionId);
            LOG.log(Level.FINE,
                    "URLRewritingModifier [{0}]: extracted {1}={2}",
                    new Object[]{node.getTestName(), argument, sessionId});
        } else {
            LOG.log(Level.FINE,
                    "URLRewritingModifier [{0}]: session parameter '{1}' not found in response",
                    new Object[]{node.getTestName(), argument});
        }
    }

    /**
     * Attempts to extract a session ID value from the response body using common patterns.
     *
     * @param body         response body text
     * @param argumentName session parameter name
     * @return extracted session ID value, or {@code null} if not found
     */
    static String extractSessionId(String body, String argumentName) {
        // Pattern: name=value in URLs (query params or path params)
        // Matches: jsessionid=ABC123, PHPSESSID=xyz, etc.
        // Value terminates at &, ;, ", ', whitespace, or >
        Pattern pattern = Pattern.compile(
                Pattern.quote(argumentName) + "=([^&;\"'\\s>]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern: ;name value (no equals, used with path_extension_no_equals)
        Pattern noEqualsPattern = Pattern.compile(
                ";" + Pattern.quote(argumentName) + "([A-Za-z0-9._-]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher noEqualsMatcher = noEqualsPattern.matcher(body);
        if (noEqualsMatcher.find()) {
            return noEqualsMatcher.group(1);
        }

        return null;
    }
}
