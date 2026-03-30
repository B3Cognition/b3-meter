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
 * Executes a {@code BoundaryExtractor} {@link PlanNode} as a post-processor.
 *
 * <p>Finds text between left and right boundary strings in the response body.
 *
 * <p>Reads the standard JMeter BoundaryExtractor properties:
 * <ul>
 *   <li>{@code BoundaryExtractor.lboundary} — left boundary string</li>
 *   <li>{@code BoundaryExtractor.rboundary} — right boundary string</li>
 *   <li>{@code BoundaryExtractor.refname} — variable name to store result</li>
 *   <li>{@code BoundaryExtractor.default} — default value if not found</li>
 *   <li>{@code BoundaryExtractor.match_number} — which match to use (1-based)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class BoundaryExtractorExecutor {

    private static final Logger LOG = Logger.getLogger(BoundaryExtractorExecutor.class.getName());

    private BoundaryExtractorExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Extracts text between boundaries from the response body and stores it in variables.
     *
     * @param node      the BoundaryExtractor node; must not be {@code null}
     * @param result    the most recent sample result; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String lBoundary   = node.getStringProp("BoundaryExtractor.lboundary", "");
        String rBoundary   = node.getStringProp("BoundaryExtractor.rboundary", "");
        String refName     = node.getStringProp("BoundaryExtractor.refname", "");
        String defaultVal  = node.getStringProp("BoundaryExtractor.default", "");
        String matchNumStr = node.getStringProp("BoundaryExtractor.match_number", "1");

        if (refName.isBlank()) {
            LOG.log(Level.WARNING, "BoundaryExtractor [{0}]: refname is empty — skipping",
                    node.getTestName());
            return;
        }

        int matchNumber;
        try {
            matchNumber = Integer.parseInt(matchNumStr.trim());
        } catch (NumberFormatException e) {
            matchNumber = 1;
        }

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            variables.put(refName, defaultVal);
            return;
        }

        List<String> matches = findBetweenBoundaries(body, lBoundary, rBoundary);

        if (matches.isEmpty()) {
            variables.put(refName, defaultVal);
            variables.put(refName + "_matchNr", "0");
            return;
        }

        variables.put(refName + "_matchNr", String.valueOf(matches.size()));

        String extracted;
        if (matchNumber == 0) {
            // Random match
            extracted = matches.get((int) (Math.random() * matches.size()));
        } else if (matchNumber == -1) {
            // All matches
            for (int i = 0; i < matches.size(); i++) {
                variables.put(refName + "_" + (i + 1), matches.get(i));
            }
            extracted = matches.get(0);
        } else {
            int idx = Math.min(matchNumber - 1, matches.size() - 1);
            extracted = matches.get(idx);
        }

        variables.put(refName, extracted);
        LOG.log(Level.FINE, "BoundaryExtractor [{0}]: {1} = {2}",
                new Object[]{node.getTestName(), refName, extracted});
    }

    /**
     * Finds all text occurrences between left and right boundaries.
     *
     * @param text       the text to search
     * @param lBoundary  left boundary string
     * @param rBoundary  right boundary string
     * @return list of matched strings between boundaries
     */
    static List<String> findBetweenBoundaries(String text, String lBoundary, String rBoundary) {
        List<String> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;

        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int leftIdx;
            if (lBoundary == null || lBoundary.isEmpty()) {
                leftIdx = searchFrom;
            } else {
                leftIdx = text.indexOf(lBoundary, searchFrom);
                if (leftIdx < 0) break;
                leftIdx += lBoundary.length();
            }

            int rightIdx;
            if (rBoundary == null || rBoundary.isEmpty()) {
                rightIdx = text.length();
            } else {
                rightIdx = text.indexOf(rBoundary, leftIdx);
                if (rightIdx < 0) break;
            }

            results.add(text.substring(leftIdx, rightIdx));

            if (rBoundary == null || rBoundary.isEmpty()) {
                break;
            }
            searchFrom = rightIdx + rBoundary.length();
        }

        return results;
    }
}
