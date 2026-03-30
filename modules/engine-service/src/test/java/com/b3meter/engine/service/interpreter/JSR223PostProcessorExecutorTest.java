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
 * Tests for {@link JSR223PostProcessorExecutor}.
 */
class JSR223PostProcessorExecutorTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJavaScriptEngine() {
        return new javax.script.ScriptEngineManager().getEngineByName("javascript") != null;
    }

    @Test
    void execute_setsVariableFromPrev() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = jsr223PostNode("javascript",
                "vars.put('status', '' + prev.getStatusCode());");
        SampleResult result = sampleWithBody("body");
        result.setStatusCode(200);

        Map<String, String> variables = new HashMap<>();
        JSR223PostProcessorExecutor.execute(node, result, variables);

        assertEquals("200", variables.get("status"));
    }

    @Test
    void execute_setsVariableFromResponseBody() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = jsr223PostNode("javascript",
                "vars.put('body', prev.getResponseBody());");
        SampleResult result = sampleWithBody("hello world");

        Map<String, String> variables = new HashMap<>();
        JSR223PostProcessorExecutor.execute(node, result, variables);

        assertEquals("hello world", variables.get("body"));
    }

    @Test
    void execute_parametersAvailable() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = PlanNode.builder("JSR223PostProcessor", "With params")
                .property("scriptLanguage", "javascript")
                .property("parameters", "alpha beta")
                .property("script", "vars.put('argCount', '' + args.length);")
                .build();

        SampleResult result = sampleWithBody("body");
        Map<String, String> variables = new HashMap<>();
        JSR223PostProcessorExecutor.execute(node, result, variables);

        assertEquals("2", variables.get("argCount"));
    }

    @Test
    void execute_blankScript_noError() {
        PlanNode node = jsr223PostNode("javascript", "");
        SampleResult result = sampleWithBody("body");
        Map<String, String> variables = new HashMap<>();
        // Should not throw
        JSR223PostProcessorExecutor.execute(node, result, variables);
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> JSR223PostProcessorExecutor.execute(null, new SampleResult("x"), new HashMap<>()));
    }

    @Test
    void execute_nullResult_throws() {
        PlanNode node = jsr223PostNode("javascript", "1+1");
        assertThrows(NullPointerException.class,
                () -> JSR223PostProcessorExecutor.execute(node, null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = jsr223PostNode("javascript", "1+1");
        assertThrows(NullPointerException.class,
                () -> JSR223PostProcessorExecutor.execute(node, new SampleResult("x"), null));
    }

    @Test
    void execute_unknownEngine_noException() {
        PlanNode node = jsr223PostNode("nonexistent-language-xyz", "vars.put('x', '1');");
        SampleResult result = sampleWithBody("body");
        Map<String, String> variables = new HashMap<>();
        // Should not throw — just logs a warning
        JSR223PostProcessorExecutor.execute(node, result, variables);
        assertNull(variables.get("x"), "Script should not execute with unknown engine");
    }

    @Test
    void execute_scriptError_noException() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = jsr223PostNode("javascript", "throw new Error('intentional');");
        SampleResult result = sampleWithBody("body");
        Map<String, String> variables = new HashMap<>();
        // Should not throw — error is caught and logged
        JSR223PostProcessorExecutor.execute(node, result, variables);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode jsr223PostNode(String language, String script) {
        return PlanNode.builder("JSR223PostProcessor", "jsr223-post")
                .property("scriptLanguage", language)
                .property("script", script)
                .property("parameters", "")
                .property("filename", "")
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
