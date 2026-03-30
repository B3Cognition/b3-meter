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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@code HTMLAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Validates HTML well-formedness by checking for unclosed tags, invalid nesting,
 * and basic structural issues. Uses a simple regex-based approach (no external libraries).
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code HTMLAssertion.ERROR_THRESHOLD} — maximum number of errors before assertion fails (default 0)</li>
 *   <li>{@code HTMLAssertion.WARNING_THRESHOLD} — maximum warnings before assertion fails (default 0)</li>
 *   <li>{@code HTMLAssertion.DOCTYPE} — expected DOCTYPE (e.g. "html", "xhtml"); empty = any</li>
 *   <li>{@code HTMLAssertion.FORMAT} — response format hint: "html" (default), "xhtml", "xml"</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HTMLAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(HTMLAssertionExecutor.class.getName());

    /** Regex to find HTML tags: opening, closing, or self-closing. */
    private static final Pattern TAG_PATTERN =
            Pattern.compile("<(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*?)(/?)\\s*>", Pattern.DOTALL);

    /** HTML5 void elements that do not require closing tags. */
    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    );

    private HTMLAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the HTML well-formedness assertion to {@code result}.
     *
     * @param node      the HTMLAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope (unused but kept for consistency)
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        int errorThreshold   = node.getIntProp("HTMLAssertion.ERROR_THRESHOLD", 0);
        int warningThreshold = node.getIntProp("HTMLAssertion.WARNING_THRESHOLD", 0);
        String doctype       = node.getStringProp("HTMLAssertion.DOCTYPE", "");
        String format        = node.getStringProp("HTMLAssertion.FORMAT", "html");

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            result.setFailureMessage("HTMLAssertion [" + node.getTestName()
                    + "] FAILED: Response body is empty — not valid HTML");
            return;
        }

        int errors = 0;
        int warnings = 0;
        StringBuilder issues = new StringBuilder();

        // Check DOCTYPE if specified
        if (!doctype.isBlank()) {
            String lowerBody = body.toLowerCase();
            if (!lowerBody.contains("<!doctype") && !lowerBody.contains("<!DOCTYPE")) {
                warnings++;
                issues.append("Missing DOCTYPE declaration; ");
            } else if (!lowerBody.contains(doctype.toLowerCase())) {
                warnings++;
                issues.append("DOCTYPE does not match expected '").append(doctype).append("'; ");
            }
        }

        // Validate tag nesting using a stack
        Deque<String> tagStack = new ArrayDeque<>();
        Matcher matcher = TAG_PATTERN.matcher(body);

        while (matcher.find()) {
            boolean isClosing     = "/".equals(matcher.group(1));
            String tagName        = matcher.group(2).toLowerCase();
            boolean isSelfClosing = "/".equals(matcher.group(4));

            if (isSelfClosing || VOID_ELEMENTS.contains(tagName)) {
                // Self-closing or void element — nothing to push/pop
                continue;
            }

            if (isClosing) {
                if (tagStack.isEmpty()) {
                    errors++;
                    issues.append("Unexpected closing tag </").append(tagName).append(">; ");
                } else if (!tagStack.peek().equals(tagName)) {
                    errors++;
                    issues.append("Mismatched tag: expected </")
                          .append(tagStack.peek())
                          .append("> but found </")
                          .append(tagName).append(">; ");
                    // Try to recover: pop if the tag exists somewhere in the stack
                    if (tagStack.contains(tagName)) {
                        while (!tagStack.isEmpty() && !tagStack.peek().equals(tagName)) {
                            tagStack.pop();
                        }
                        if (!tagStack.isEmpty()) {
                            tagStack.pop();
                        }
                    }
                } else {
                    tagStack.pop();
                }
            } else {
                // Opening tag
                tagStack.push(tagName);
            }
        }

        // Any remaining unclosed tags
        while (!tagStack.isEmpty()) {
            errors++;
            issues.append("Unclosed tag <").append(tagStack.pop()).append(">; ");
        }

        // Evaluate thresholds
        if (errors > errorThreshold || warnings > warningThreshold) {
            result.setFailureMessage("HTMLAssertion [" + node.getTestName()
                    + "] FAILED: " + errors + " error(s), " + warnings + " warning(s). "
                    + issues.toString().trim());
        }

        LOG.log(Level.FINE, "HTMLAssertion [{0}]: {1} error(s), {2} warning(s)",
                new Object[]{node.getTestName(), errors, warnings});
    }
}
