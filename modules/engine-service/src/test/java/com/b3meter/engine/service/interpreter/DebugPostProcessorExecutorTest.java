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
 * Tests for {@link DebugPostProcessorExecutor}.
 */
class DebugPostProcessorExecutorTest {

    @Test
    void dumpsVariables() {
        PlanNode node = debugNode(true, false, false);
        Map<String, String> vars = new HashMap<>();
        vars.put("user", "alice");
        vars.put("token", "xyz");

        SampleResult result = sampleWithBody("");
        DebugPostProcessorExecutor.execute(node, result, vars);

        String body = result.getResponseBody();
        assertTrue(body.contains("[JMeter Variables]"));
        assertTrue(body.contains("user=alice"));
        assertTrue(body.contains("token=xyz"));
    }

    @Test
    void dumpsProperties() {
        PlanNode node = debugNode(false, true, false);
        SampleResult result = sampleWithBody("");
        DebugPostProcessorExecutor.execute(node, result, new HashMap<>());

        String body = result.getResponseBody();
        assertTrue(body.contains("[JMeter Properties]"));
        // System properties are dumped; at least java.version should be present
        assertTrue(body.contains("java.version="));
    }

    @Test
    void noFlags_emptyOutput() {
        PlanNode node = debugNode(false, false, false);
        SampleResult result = sampleWithBody("");
        DebugPostProcessorExecutor.execute(node, result, new HashMap<>());
        assertEquals("", result.getResponseBody());
    }

    @Test
    void appendsToExistingBody() {
        PlanNode node = debugNode(true, false, false);
        Map<String, String> vars = new HashMap<>();
        vars.put("key", "val");

        SampleResult result = sampleWithBody("original body");
        DebugPostProcessorExecutor.execute(node, result, vars);

        String body = result.getResponseBody();
        assertTrue(body.startsWith("original body"));
        assertTrue(body.contains("--- Debug PostProcessor ---"));
        assertTrue(body.contains("key=val"));
    }

    @Test
    void variablesSorted() {
        PlanNode node = debugNode(true, false, false);
        Map<String, String> vars = new HashMap<>();
        vars.put("z_var", "last");
        vars.put("a_var", "first");

        SampleResult result = sampleWithBody("");
        DebugPostProcessorExecutor.execute(node, result, vars);

        String body = result.getResponseBody();
        int aIdx = body.indexOf("a_var=first");
        int zIdx = body.indexOf("z_var=last");
        assertTrue(aIdx < zIdx, "Variables should be sorted alphabetically");
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                DebugPostProcessorExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode debugNode(boolean displayVars, boolean displayProps, boolean displaySys) {
        return PlanNode.builder("DebugPostProcessor", "debug-pp")
                .property("displayJMeterVariables", displayVars)
                .property("displayJMeterProperties", displayProps)
                .property("displaySystemProperties", displaySys)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
