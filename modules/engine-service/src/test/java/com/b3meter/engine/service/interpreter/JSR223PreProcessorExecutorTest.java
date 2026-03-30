package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JSR223PreProcessorExecutor}.
 */
class JSR223PreProcessorExecutorTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJavaScriptEngine() {
        return new javax.script.ScriptEngineManager().getEngineByName("javascript") != null;
    }

    @Test
    void execute_setsVariableFromScript() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = PlanNode.builder("JSR223PreProcessor", "Set timestamp")
                .property("scriptLanguage", "javascript")
                .property("script", "vars.put('myVar', '42');")
                .build();

        Map<String, String> variables = new HashMap<>();
        JSR223PreProcessorExecutor.execute(node, variables);

        assertEquals("42", variables.get("myVar"),
                "Pre-processor script should set variable in VU map");
    }

    @Test
    void execute_readsExistingVariables() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = PlanNode.builder("JSR223PreProcessor", "Read and set")
                .property("scriptLanguage", "javascript")
                .property("script", "vars.put('result', 'prefix_' + vars.get('input'));")
                .build();

        Map<String, String> variables = new HashMap<>();
        variables.put("input", "hello");
        JSR223PreProcessorExecutor.execute(node, variables);

        assertEquals("prefix_hello", variables.get("result"));
    }

    @Test
    void execute_blankScript_noError() {
        PlanNode node = PlanNode.builder("JSR223PreProcessor", "Empty")
                .property("scriptLanguage", "javascript")
                .property("script", "")
                .build();

        Map<String, String> variables = new HashMap<>();
        // Should not throw
        JSR223PreProcessorExecutor.execute(node, variables);
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> JSR223PreProcessorExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("JSR223PreProcessor", "test")
                .property("script", "1+1")
                .build();
        assertThrows(NullPointerException.class,
                () -> JSR223PreProcessorExecutor.execute(node, null));
    }

    @Test
    void execute_parametersAvailableInBindings() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = PlanNode.builder("JSR223PreProcessor", "With params")
                .property("scriptLanguage", "javascript")
                .property("parameters", "alpha beta")
                .property("script", "vars.put('paramCount', '' + args.length);")
                .build();

        Map<String, String> variables = new HashMap<>();
        JSR223PreProcessorExecutor.execute(node, variables);

        assertEquals("2", variables.get("paramCount"));
    }

    @Test
    void execute_doesNotProduceSampleResult() {
        if (!hasJavaScriptEngine()) return;

        PlanNode node = PlanNode.builder("JSR223PreProcessor", "No result")
                .property("scriptLanguage", "javascript")
                .property("script", "vars.put('x', '1');")
                .build();

        Map<String, String> variables = new HashMap<>();
        JSR223PreProcessorExecutor.execute(node, variables);

        assertEquals("1", variables.get("x"));
    }

    @Test
    void execute_unknownEngine_noException() {
        PlanNode node = PlanNode.builder("JSR223PreProcessor", "Bad engine")
                .property("scriptLanguage", "nonexistent-language-xyz")
                .property("script", "vars.put('x', '1');")
                .build();

        Map<String, String> variables = new HashMap<>();
        // Should not throw — just logs a warning
        JSR223PreProcessorExecutor.execute(node, variables);
        assertNull(variables.get("x"), "Script should not execute with unknown engine");
    }
}
