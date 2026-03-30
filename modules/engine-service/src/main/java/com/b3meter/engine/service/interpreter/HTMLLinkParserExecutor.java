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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@code HTMLLinkParser} {@link PlanNode} as a pre-processor.
 *
 * <p>Parses the most recent HTML response for links ({@code <a href="...">}) and
 * form actions ({@code <form action="...">}), and stores the extracted URLs
 * as variables for the next sampler to use.
 *
 * <p>Extracted URLs are stored as:
 * <ul>
 *   <li>{@code __jmeter_link_N} — Nth link URL (1-indexed)</li>
 *   <li>{@code __jmeter_link_count} — total number of links found</li>
 *   <li>{@code __jmeter_form_N} — Nth form action URL (1-indexed)</li>
 *   <li>{@code __jmeter_form_count} — total number of forms found</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HTMLLinkParserExecutor {

    private static final Logger LOG = Logger.getLogger(HTMLLinkParserExecutor.class.getName());

    /** Regex to extract href attributes from anchor tags. */
    private static final Pattern HREF_PATTERN =
            Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Regex to extract action attributes from form tags. */
    private static final Pattern FORM_PATTERN =
            Pattern.compile("<form\\s+[^>]*action\\s*=\\s*[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private HTMLLinkParserExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Parses links and form actions from the last response and stores them as variables.
     *
     * @param node       the HTMLLinkParser node; must not be {@code null}
     * @param lastResult the most recent sample result to parse; may be {@code null} (no-op)
     * @param variables  current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult lastResult, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        if (lastResult == null) {
            LOG.log(Level.FINE,
                    "HTMLLinkParser [{0}]: no previous result — skipping",
                    node.getTestName());
            return;
        }

        String body = lastResult.getResponseBody();
        if (body == null || body.isBlank()) {
            LOG.log(Level.FINE,
                    "HTMLLinkParser [{0}]: empty response body — skipping",
                    node.getTestName());
            return;
        }

        // Extract links
        List<String> links = extractAll(body, HREF_PATTERN);
        variables.put("__jmeter_link_count", String.valueOf(links.size()));
        for (int i = 0; i < links.size(); i++) {
            variables.put("__jmeter_link_" + (i + 1), links.get(i));
        }

        // Extract form actions
        List<String> forms = extractAll(body, FORM_PATTERN);
        variables.put("__jmeter_form_count", String.valueOf(forms.size()));
        for (int i = 0; i < forms.size(); i++) {
            variables.put("__jmeter_form_" + (i + 1), forms.get(i));
        }

        LOG.log(Level.FINE, "HTMLLinkParser [{0}]: found {1} link(s), {2} form(s)",
                new Object[]{node.getTestName(), links.size(), forms.size()});
    }

    private static List<String> extractAll(String body, Pattern pattern) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                results.add(value);
            }
        }
        return results;
    }
}
