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
 * Executes a {@code RegExUserParameters} {@link PlanNode} pre-processor.
 *
 * <p>Reads regex reference names from the node properties and extracts matching
 * values from the last sampler's response body, setting them as VU variables.
 * This combines extraction and parameterization in one element.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code RegExUserParameters.regex_ref_name} — the variable reference name
 *       prefix. The executor looks for variables named {@code refName_g0}, {@code refName_g1},
 *       etc. (set by a previous RegexExtractor) and re-publishes them as usable variables.</li>
 *   <li>{@code RegExUserParameters.param_names_gr_nr} — group number for parameter names</li>
 *   <li>{@code RegExUserParameters.param_values_gr_nr} — group number for parameter values</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RegExUserParametersPreProcessorExecutor {

    private static final Logger LOG =
            Logger.getLogger(RegExUserParametersPreProcessorExecutor.class.getName());

    private RegExUserParametersPreProcessorExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the RegExUserParameters pre-processor using the last sample result
     * to extract regex matches and set them as variables.
     *
     * @param node       the RegExUserParameters node; must not be {@code null}
     * @param lastResult the most recent sample result (source for extraction); may be {@code null}
     * @param variables  current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult lastResult,
                               Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String regexRefName = node.getStringProp("RegExUserParameters.regex_ref_name", "");

        if (regexRefName.isBlank()) {
            LOG.log(Level.FINE,
                    "RegExUserParametersPreProcessorExecutor [{0}]: no regex_ref_name — skipping",
                    node.getTestName());
            return;
        }

        String paramNamesGroupStr = node.getStringProp(
                "RegExUserParameters.param_names_gr_nr", "1");
        String paramValuesGroupStr = node.getStringProp(
                "RegExUserParameters.param_values_gr_nr", "1");

        int paramNamesGroup;
        int paramValuesGroup;
        try {
            paramNamesGroup = Integer.parseInt(paramNamesGroupStr);
            paramValuesGroup = Integer.parseInt(paramValuesGroupStr);
        } catch (NumberFormatException e) {
            paramNamesGroup = 1;
            paramValuesGroup = 1;
        }

        // Look for previously extracted variables with the refName prefix
        // Pattern: refName_matchNr (1-based), refName_matchNr_gN (group N)
        // Also check for the simple refName variable
        String refValue = variables.get(regexRefName);
        if (refValue != null && !refValue.isEmpty()) {
            // If a simple reference exists, use it directly
            variables.put(regexRefName, refValue);
            LOG.log(Level.FINE,
                    "RegExUserParametersPreProcessorExecutor [{0}]: direct ref {1} = {2}",
                    new Object[]{node.getTestName(), regexRefName, refValue});
        }

        // Iterate over indexed variables (refName_1, refName_2, ...) and their groups
        for (int matchIdx = 1; ; matchIdx++) {
            String matchKey = regexRefName + "_" + matchIdx;
            String matchValue = variables.get(matchKey);
            if (matchValue == null) break;

            // Check group values: refName_matchIdx_g0, refName_matchIdx_g1, ...
            String nameKey = matchKey + "_g" + paramNamesGroup;
            String valueKey = matchKey + "_g" + paramValuesGroup;

            String paramName = variables.getOrDefault(nameKey, matchKey);
            String paramValue = variables.getOrDefault(valueKey, matchValue);

            variables.put(paramName, paramValue);
            LOG.log(Level.FINE,
                    "RegExUserParametersPreProcessorExecutor [{0}]: set {1} = {2}",
                    new Object[]{node.getTestName(), paramName, paramValue});
        }
    }
}
