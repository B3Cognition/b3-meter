package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JSR223AssertionExecutor}.
 */
class JSR223AssertionExecutorTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJsEngine() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        return mgr.getEngineByName("javascript") != null || mgr.getEngineByName("js") != null;
    }

    @Test
    void scriptPasses_noFailure() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "var x = 1;"); // no failure set
        SampleResult result = sampleWithBody("test body");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess(), "Failure message: " + result.getFailureMessage());
    }

    @Test
    void scriptSetsFailure() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "AssertionResult.setFailure(true); AssertionResult.setFailureMessage('custom error');");
        SampleResult result = sampleWithBody("test body");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("custom error"));
    }

    @Test
    void scriptCanAccessSampleResult() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "if (SampleResult.getResponseBody() !== 'expected') { " +
                        "AssertionResult.setFailure(true); " +
                        "AssertionResult.setFailureMessage('body mismatch'); }");
        SampleResult result = sampleWithBody("expected");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void scriptCanAccessResponse() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "if (Response !== 'my data') { " +
                        "AssertionResult.setFailure(true); " +
                        "AssertionResult.setFailureMessage('wrong response'); }");
        SampleResult result = sampleWithBody("my data");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void scriptCanAccessVars() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "vars.put('test_var', 'hello');");
        SampleResult result = sampleWithBody("body");
        Map<String, String> vars = new HashMap<>();
        JSR223AssertionExecutor.execute(node, result, vars);
        assertTrue(result.isSuccess());
        assertEquals("hello", vars.get("test_var"));
    }

    @Test
    void scriptError_marksFailure() {
        if (!hasJsEngine()) return;
        PlanNode node = jsr223AssertionNode("javascript",
                "throw new Error('intentional');");
        SampleResult result = sampleWithBody("body");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("script error"));
    }

    @Test
    void emptyScript_fails() {
        PlanNode node = jsr223AssertionNode("javascript", "");
        SampleResult result = sampleWithBody("body");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no script"));
    }

    @Test
    void unknownEngine_fails() {
        PlanNode node = jsr223AssertionNode("unknownlang", "some code");
        SampleResult result = sampleWithBody("body");
        JSR223AssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("no ScriptEngine"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                JSR223AssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode jsr223AssertionNode(String language, String script) {
        return PlanNode.builder("JSR223Assertion", "jsr223-assertion")
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
