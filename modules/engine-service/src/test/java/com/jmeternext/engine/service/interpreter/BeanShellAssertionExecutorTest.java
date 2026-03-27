package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BeanShellAssertionExecutor}.
 */
class BeanShellAssertionExecutorTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJsEngine() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        return mgr.getEngineByName("javascript") != null || mgr.getEngineByName("js") != null;
    }

    @Test
    void emptyScript_noFailure() {
        PlanNode node = beanShellAssertionNode("");
        SampleResult result = sampleWithBody("test body");
        // Empty script should delegate to JSR223 which reports "no script"
        BeanShellAssertionExecutor.execute(node, result, new HashMap<>());
        // JSR223 marks failure for empty script
        assertFalse(result.isSuccess());
    }

    @Test
    void scriptCanAccessSampleResult() {
        if (!hasJsEngine()) return;
        // BeanShell falls back to javascript when bsh engine is not available
        PlanNode node = PlanNode.builder("BeanShellAssertion", "bsh-assertion")
                .property("BeanShellAssertion.script",
                        "if (SampleResult.getResponseBody() !== 'expected') { " +
                        "AssertionResult.setFailure(true); " +
                        "AssertionResult.setFailureMessage('mismatch'); }")
                .property("BeanShellAssertion.filename", "")
                .property("BeanShellAssertion.parameters", "")
                .build();
        SampleResult result = sampleWithBody("expected");
        BeanShellAssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void scriptSetsFailure() {
        if (!hasJsEngine()) return;
        PlanNode node = PlanNode.builder("BeanShellAssertion", "bsh-assertion")
                .property("BeanShellAssertion.script",
                        "AssertionResult.setFailure(true); " +
                        "AssertionResult.setFailureMessage('bsh error');")
                .property("BeanShellAssertion.filename", "")
                .property("BeanShellAssertion.parameters", "")
                .build();
        SampleResult result = sampleWithBody("body");
        BeanShellAssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("bsh error"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                BeanShellAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    @Test
    void nullResult_throws() {
        PlanNode node = beanShellAssertionNode("");
        assertThrows(NullPointerException.class, () ->
                BeanShellAssertionExecutor.execute(node, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode beanShellAssertionNode(String script) {
        return PlanNode.builder("BeanShellAssertion", "bsh-assertion")
                .property("BeanShellAssertion.script", script)
                .property("BeanShellAssertion.filename", "")
                .property("BeanShellAssertion.parameters", "")
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
