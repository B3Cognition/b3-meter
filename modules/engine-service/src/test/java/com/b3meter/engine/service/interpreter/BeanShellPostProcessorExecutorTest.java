package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BeanShellPostProcessorExecutor}.
 */
class BeanShellPostProcessorExecutorTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJsEngine() {
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        return mgr.getEngineByName("javascript") != null || mgr.getEngineByName("js") != null;
    }

    @Test
    void scriptSetsVariable() {
        if (!hasJsEngine()) return;
        PlanNode node = beanShellPostNode("vars.put('extracted', 'value123');");
        SampleResult result = sampleWithBody("body");
        Map<String, String> vars = new HashMap<>();
        BeanShellPostProcessorExecutor.execute(node, result, vars);
        assertEquals("value123", vars.get("extracted"));
    }

    @Test
    void scriptAccessesPrevResult() {
        if (!hasJsEngine()) return;
        PlanNode node = beanShellPostNode("vars.put('body_len', '' + prev.getResponseBody().length());");
        SampleResult result = sampleWithBody("hello");
        Map<String, String> vars = new HashMap<>();
        BeanShellPostProcessorExecutor.execute(node, result, vars);
        assertEquals("5", vars.get("body_len"));
    }

    @Test
    void emptyScript_noError() {
        PlanNode node = beanShellPostNode("");
        SampleResult result = sampleWithBody("body");
        Map<String, String> vars = new HashMap<>();
        // Should not throw — delegates to JSR223 which skips blank scripts
        BeanShellPostProcessorExecutor.execute(node, result, vars);
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                BeanShellPostProcessorExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    @Test
    void nullResult_throws() {
        PlanNode node = beanShellPostNode("");
        assertThrows(NullPointerException.class, () ->
                BeanShellPostProcessorExecutor.execute(node, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode beanShellPostNode(String script) {
        return PlanNode.builder("BeanShellPostProcessor", "bsh-post")
                .property("BeanShellPostProcessor.script", script)
                .property("BeanShellPostProcessor.filename", "")
                .property("BeanShellPostProcessor.parameters", "")
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
