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
 * Tests for {@link BeanShellSamplerExecutor}.
 */
class BeanShellSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("bsh-test");
        assertThrows(NullPointerException.class,
                () -> BeanShellSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = bshNode("1 + 1");
        assertThrows(NullPointerException.class,
                () -> BeanShellSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = bshNode("1 + 1");
        SampleResult result = new SampleResult("bsh-test");
        assertThrows(NullPointerException.class,
                () -> BeanShellSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty script
    // =========================================================================

    @Test
    void execute_failsOnEmptyScript() {
        PlanNode node = bshNode("");
        SampleResult result = new SampleResult("bsh-empty");

        BeanShellSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no script")
                || result.getFailureMessage().contains("no ScriptEngine"));
    }

    // =========================================================================
    // Delegation to JSR223
    // =========================================================================

    @Test
    void execute_delegatesToJSR223() {
        // BeanShell engine may not be available; the executor falls back to JavaScript.
        // If JS is not available either (JDK 21), the test passes with a failure message.
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        boolean hasJs = mgr.getEngineByName("javascript") != null;
        boolean hasBsh = mgr.getEngineByName("beanshell") != null;

        if (!hasJs && !hasBsh) return; // No engine available at all

        PlanNode node = PlanNode.builder("BeanShellSampler", "bsh-delegate")
                .property("BeanShellSampler.script",
                        "SampleResult.setResponseBody('delegated');")
                .build();

        SampleResult result = new SampleResult("bsh-delegate");
        BeanShellSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("delegated", result.getResponseBody());
    }

    @Test
    void execute_passesParametersThrough() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null
                && mgr.getEngineByName("beanshell") == null) return;

        PlanNode node = PlanNode.builder("BeanShellSampler", "bsh-params")
                .property("BeanShellSampler.script",
                        "SampleResult.setResponseBody(Parameters);")
                .property("BeanShellSampler.parameters", "alpha beta")
                .build();

        SampleResult result = new SampleResult("bsh-params");
        BeanShellSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("alpha beta", result.getResponseBody());
    }

    @Test
    void execute_exposesVariables() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null
                && mgr.getEngineByName("beanshell") == null) return;

        Map<String, String> vars = new HashMap<>();
        vars.put("key", "value");

        PlanNode node = PlanNode.builder("BeanShellSampler", "bsh-vars")
                .property("BeanShellSampler.script",
                        "SampleResult.setResponseBody(vars.get('key'));")
                .build();

        SampleResult result = new SampleResult("bsh-vars");
        BeanShellSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertEquals("value", result.getResponseBody());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode bshNode(String script) {
        return PlanNode.builder("BeanShellSampler", "bsh-test")
                .property("BeanShellSampler.script", script)
                .build();
    }
}
