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
 * Tests for {@link DebugSamplerExecutor}.
 */
class DebugSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("debug-test");
        assertThrows(NullPointerException.class,
                () -> DebugSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = debugNode(true, false);
        assertThrows(NullPointerException.class,
                () -> DebugSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = debugNode(true, false);
        SampleResult result = new SampleResult("debug-test");
        assertThrows(NullPointerException.class,
                () -> DebugSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Variable display
    // =========================================================================

    @Test
    void execute_displaysVariables() {
        Map<String, String> vars = new HashMap<>();
        vars.put("host", "example.com");
        vars.put("port", "8080");

        PlanNode node = debugNode(true, false);
        SampleResult result = new SampleResult("debug-vars");

        DebugSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("host=example.com"));
        assertTrue(result.getResponseBody().contains("port=8080"));
        assertTrue(result.getResponseBody().contains("JMeter Variables"));
    }

    @Test
    void execute_emptyVariablesShowsNone() {
        PlanNode node = debugNode(true, false);
        SampleResult result = new SampleResult("debug-empty");

        DebugSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("(none)"));
    }

    @Test
    void execute_skipsVariablesWhenDisabled() {
        Map<String, String> vars = new HashMap<>();
        vars.put("key", "value");

        PlanNode node = debugNode(false, false);
        SampleResult result = new SampleResult("debug-no-vars");

        DebugSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertFalse(result.getResponseBody().contains("key=value"));
        assertFalse(result.getResponseBody().contains("JMeter Variables"));
    }

    // =========================================================================
    // System properties display
    // =========================================================================

    @Test
    void execute_displaysSystemProperties() {
        PlanNode node = debugNode(false, true);
        SampleResult result = new SampleResult("debug-sysprops");

        DebugSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("System Properties"));
        // java.version is always set
        assertTrue(result.getResponseBody().contains("java.version="));
    }

    @Test
    void execute_displaysBothVariablesAndSysProps() {
        Map<String, String> vars = new HashMap<>();
        vars.put("myVar", "myVal");

        PlanNode node = debugNode(true, true);
        SampleResult result = new SampleResult("debug-both");

        DebugSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("JMeter Variables"));
        assertTrue(result.getResponseBody().contains("myVar=myVal"));
        assertTrue(result.getResponseBody().contains("System Properties"));
        assertTrue(result.getResponseBody().contains("java.version="));
    }

    // =========================================================================
    // Default property values
    // =========================================================================

    @Test
    void execute_defaultsToDisplayVariablesOnly() {
        Map<String, String> vars = new HashMap<>();
        vars.put("x", "1");

        // Node with no explicit boolean properties — defaults should be displayVars=true, displaySysProps=false
        PlanNode node = PlanNode.builder("DebugSampler", "debug-defaults").build();
        SampleResult result = new SampleResult("debug-defaults");

        DebugSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("x=1"));
        assertFalse(result.getResponseBody().contains("System Properties"));
    }

    // =========================================================================
    // Always succeeds
    // =========================================================================

    @Test
    void execute_alwaysSucceeds() {
        PlanNode node = debugNode(true, true);
        SampleResult result = new SampleResult("always-success");

        DebugSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Sorted output
    // =========================================================================

    @Test
    void execute_variablesSortedAlphabetically() {
        Map<String, String> vars = new HashMap<>();
        vars.put("zebra", "z");
        vars.put("alpha", "a");
        vars.put("middle", "m");

        PlanNode node = debugNode(true, false);
        SampleResult result = new SampleResult("debug-sorted");

        DebugSamplerExecutor.execute(node, result, vars);

        String body = result.getResponseBody();
        int alphaIdx = body.indexOf("alpha=a");
        int middleIdx = body.indexOf("middle=m");
        int zebraIdx = body.indexOf("zebra=z");

        assertTrue(alphaIdx < middleIdx, "alpha should come before middle");
        assertTrue(middleIdx < zebraIdx, "middle should come before zebra");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode debugNode(boolean displayVars, boolean displaySysProps) {
        return PlanNode.builder("DebugSampler", "Debug Sampler")
                .property("DebugSampler.displayJMeterVariables", displayVars)
                .property("DebugSampler.displaySystemProperties", displaySysProps)
                .build();
    }
}
