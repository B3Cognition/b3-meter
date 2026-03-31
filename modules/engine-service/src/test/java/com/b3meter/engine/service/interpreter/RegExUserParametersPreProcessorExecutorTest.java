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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RegExUserParametersPreProcessorExecutor}.
 *
 * <p>The executor reads previously extracted regex-group variables (set by an
 * upstream RegexExtractor, e.g. {@code myRef_1_g1}) from the variable map and
 * re-publishes them as named parameters. Tests pre-populate the variable map
 * to simulate what a RegexExtractor would have done.
 */
class RegExUserParametersPreProcessorExecutorTest {

    // =========================================================================
    // Test 1: Matched pattern injects named variable
    // =========================================================================

    @Test
    void matchedPatternInjectsNamedVariable() {
        // Simulate a prior RegexExtractor that matched pattern (\w+)@(\w+)
        // against "user@domain" and set:
        //   myRef_1      = "user@domain"   (full match)
        //   myRef_1_g1   = "user"          (group 1)
        //   myRef_1_g2   = "domain"        (group 2)
        Map<String, String> vars = new HashMap<>();
        vars.put("myRef_1", "user@domain");
        vars.put("myRef_1_g1", "user");
        vars.put("myRef_1_g2", "domain");

        // param_names_gr_nr=1 → use group 1 value as the parameter name
        // param_values_gr_nr=2 → use group 2 value as the parameter value
        PlanNode node = PlanNode.builder("RegExUserParameters", "regex-params")
                .property("RegExUserParameters.regex_ref_name", "myRef")
                .property("RegExUserParameters.param_names_gr_nr", "1")
                .property("RegExUserParameters.param_values_gr_nr", "2")
                .build();

        RegExUserParametersPreProcessorExecutor.execute(node, null, vars);

        // Group 1 = "user" becomes the variable name; group 2 = "domain" becomes the value
        assertEquals("domain", vars.get("user"),
                "Variable 'user' should have been injected with value 'domain'");
    }

    // =========================================================================
    // Test 2: Unmatched pattern (no indexed variables) produces no injection, no exception
    // =========================================================================

    @Test
    void unmatchedPatternProducesNoInjection() {
        // No variables set — simulates a RegexExtractor that found no match
        Map<String, String> vars = new HashMap<>();
        int initialSize = vars.size();

        PlanNode node = PlanNode.builder("RegExUserParameters", "no-match")
                .property("RegExUserParameters.regex_ref_name", "noMatchRef")
                .property("RegExUserParameters.param_names_gr_nr", "1")
                .property("RegExUserParameters.param_values_gr_nr", "2")
                .build();

        assertDoesNotThrow(() ->
                RegExUserParametersPreProcessorExecutor.execute(node, null, vars));

        // No variable should have been added or set to null
        for (String val : vars.values()) {
            assertNotNull(val, "Variable values must never be null");
        }
        // No new keys injected (no match means no new variables)
        assertEquals(initialSize, vars.size(),
                "No new variables should be injected when there is no match");
    }

    // =========================================================================
    // Test 3: Multiple patterns on same input — independent variable injection
    // =========================================================================

    @Test
    void multiplePatternsIndependentInjection() {
        // Simulate two separate RegexExtractors producing two ref sets:
        //   ref1_1, ref1_1_g1, ref1_1_g2
        //   ref2_1, ref2_1_g1, ref2_1_g2
        Map<String, String> vars = new HashMap<>();
        vars.put("ref1_1", "alice@example.com");
        vars.put("ref1_1_g1", "alice");
        vars.put("ref1_1_g2", "example.com");

        vars.put("ref2_1", "bob@test.org");
        vars.put("ref2_1_g1", "bob");
        vars.put("ref2_1_g2", "test.org");

        PlanNode node1 = PlanNode.builder("RegExUserParameters", "pattern-1")
                .property("RegExUserParameters.regex_ref_name", "ref1")
                .property("RegExUserParameters.param_names_gr_nr", "1")
                .property("RegExUserParameters.param_values_gr_nr", "2")
                .build();

        PlanNode node2 = PlanNode.builder("RegExUserParameters", "pattern-2")
                .property("RegExUserParameters.regex_ref_name", "ref2")
                .property("RegExUserParameters.param_names_gr_nr", "1")
                .property("RegExUserParameters.param_values_gr_nr", "2")
                .build();

        RegExUserParametersPreProcessorExecutor.execute(node1, null, vars);
        RegExUserParametersPreProcessorExecutor.execute(node2, null, vars);

        // ref1 pattern: name=group1("alice"), value=group2("example.com")
        assertEquals("example.com", vars.get("alice"),
                "Variable 'alice' should be 'example.com'");

        // ref2 pattern: name=group1("bob"), value=group2("test.org")
        assertEquals("test.org", vars.get("bob"),
                "Variable 'bob' should be 'test.org'");
    }
}
