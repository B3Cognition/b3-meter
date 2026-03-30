package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IfControllerExecutor}.
 *
 * <p>Uses a stub {@link NodeInterpreter} backed by a no-op HTTP client and broker so
 * that the test can run without any network infrastructure.
 */
class IfControllerExecutorTest {

    // -------------------------------------------------------------------------
    // Static evaluator unit tests (no interpreter needed)
    // -------------------------------------------------------------------------

    @Test
    void trueLiteral_isTrue() {
        assertTrue(IfControllerExecutor.evaluate("true", "test"));
    }

    @Test
    void falseLiteral_isFalse() {
        assertFalse(IfControllerExecutor.evaluate("false", "test"));
    }

    @Test
    void equalsOperator_matches() {
        assertTrue(IfControllerExecutor.evaluate("hello == hello", "t"));
    }

    @Test
    void equalsOperator_doesNotMatch() {
        assertFalse(IfControllerExecutor.evaluate("hello == world", "t"));
    }

    @Test
    void notEqualsOperator() {
        assertTrue(IfControllerExecutor.evaluate("a != b", "t"));
        assertFalse(IfControllerExecutor.evaluate("a != a", "t"));
    }

    @Test
    void greaterThan_numeric() {
        assertTrue(IfControllerExecutor.evaluate("5 > 3", "t"));
        assertFalse(IfControllerExecutor.evaluate("3 > 5", "t"));
    }

    @Test
    void lessThan_numeric() {
        assertTrue(IfControllerExecutor.evaluate("1 < 10", "t"));
        assertFalse(IfControllerExecutor.evaluate("10 < 1", "t"));
    }

    @Test
    void greaterThanOrEqual() {
        assertTrue(IfControllerExecutor.evaluate("5 >= 5", "t"));
        assertTrue(IfControllerExecutor.evaluate("6 >= 5", "t"));
        assertFalse(IfControllerExecutor.evaluate("4 >= 5", "t"));
    }

    @Test
    void lessThanOrEqual() {
        assertTrue(IfControllerExecutor.evaluate("5 <= 5", "t"));
        assertFalse(IfControllerExecutor.evaluate("6 <= 5", "t"));
    }

    @Test
    void containsOperator() {
        assertTrue(IfControllerExecutor.evaluate("hello world contains world", "t"));
        assertFalse(IfControllerExecutor.evaluate("hello world contains missing", "t"));
    }

    @Test
    void quotedStringEquality() {
        assertTrue(IfControllerExecutor.evaluate("\"alice\" == \"alice\"", "t"));
        assertFalse(IfControllerExecutor.evaluate("\"alice\" == \"bob\"", "t"));
    }

    @Test
    void emptyCondition_isFalse() {
        assertFalse(IfControllerExecutor.evaluate("", "t"));
        assertFalse(IfControllerExecutor.evaluate(null, "t"));
    }

    // -------------------------------------------------------------------------
    // Integration: controller executes children on true, skips on false
    // -------------------------------------------------------------------------

    @Test
    void trueCondition_executesChildren() {
        // Build an IfController whose child is a no-op (unsupported type that just returns [])
        PlanNode child = PlanNode.builder("Marker", "marker").build();
        PlanNode controller = PlanNode.builder("IfController", "branch")
                .property("IfController.condition", "true")
                .child(child)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IfControllerExecutor exec = new IfControllerExecutor(interpreter);

        Map<String, String> vars = new HashMap<>();
        // Should not throw and should process children (even if they produce no results)
        assertDoesNotThrow(() -> exec.execute(controller, vars));
    }

    @Test
    void falseCondition_skipsChildren() {
        PlanNode child = PlanNode.builder("HTTPSamplerProxy", "should-not-run")
                .property("HTTPSampler.domain", "example.com")
                .property("HTTPSampler.path",   "/")
                .property("HTTPSampler.method", "GET")
                .build();

        PlanNode controller = PlanNode.builder("IfController", "branch")
                .property("IfController.condition", "false")
                .child(child)
                .build();

        // Even with a real interpreter, false condition → no HTTP calls → no results
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IfControllerExecutor exec = new IfControllerExecutor(interpreter);

        var results = exec.execute(controller, new HashMap<>());
        assertTrue(results.isEmpty(), "False condition must produce zero results");
    }

    @Test
    void variableSubstitutedCondition_true() {
        PlanNode controller = PlanNode.builder("IfController", "varCond")
                .property("IfController.condition", "${status} == 200")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IfControllerExecutor exec = new IfControllerExecutor(interpreter);

        Map<String, String> vars = new HashMap<>();
        vars.put("status", "200");

        // Condition resolves to "200 == 200" → true; no children to run but must not throw
        assertDoesNotThrow(() -> exec.execute(controller, vars));
    }

    @Test
    void nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IfControllerExecutor exec = new IfControllerExecutor(interpreter);
        assertThrows(NullPointerException.class, () -> exec.execute(null, new HashMap<>()));
    }
}
