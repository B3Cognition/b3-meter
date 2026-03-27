package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SwitchControllerExecutor}.
 */
class SwitchControllerExecutorTest {

    @Test
    void execute_numericIndex_selectsCorrectChild() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "Child A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "Child B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "Child C").build();

        PlanNode switchNode = PlanNode.builder("SwitchController", "Switch")
                .property("SwitchController.value", "1")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(switchNode, variables);

        assertEquals(1, results.size(), "Should execute exactly one child");
        assertEquals("Child B", results.get(0).getLabel(),
                "Should select child at index 1");
    }

    @Test
    void execute_nameMatch_selectsCorrectChild() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "Login").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "Browse").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "Checkout").build();

        PlanNode switchNode = PlanNode.builder("SwitchController", "Switch")
                .property("SwitchController.value", "Browse")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(switchNode, variables);

        assertEquals(1, results.size());
        assertEquals("Browse", results.get(0).getLabel());
    }

    @Test
    void execute_noMatch_defaultsToFirstChild() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "Default").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "Other").build();

        PlanNode switchNode = PlanNode.builder("SwitchController", "Switch")
                .property("SwitchController.value", "nonexistent")
                .child(child0)
                .child(child1)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(switchNode, variables);

        assertEquals(1, results.size());
        assertEquals("Default", results.get(0).getLabel(),
                "Should fall back to child at index 0");
    }

    @Test
    void execute_outOfRangeIndex_defaultsToFirstChild() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "Default").build();

        PlanNode switchNode = PlanNode.builder("SwitchController", "Switch")
                .property("SwitchController.value", "99")
                .child(child0)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(switchNode, variables);

        assertEquals(1, results.size());
        assertEquals("Default", results.get(0).getLabel());
    }

    @Test
    void execute_variableResolution_selectsByResolvedValue() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();

        PlanNode switchNode = PlanNode.builder("SwitchController", "Switch")
                .property("SwitchController.value", "${switchVar}")
                .child(child0)
                .child(child1)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();
        variables.put("switchVar", "1");

        List<SampleResult> results = executor.execute(switchNode, variables);

        assertEquals(1, results.size());
        assertEquals("B", results.get(0).getLabel(),
                "Should resolve variable and select child at index 1");
    }

    @Test
    void execute_noChildren_returnsEmpty() {
        PlanNode switchNode = PlanNode.builder("SwitchController", "Empty Switch")
                .property("SwitchController.value", "0")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(switchNode, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("SwitchController", "test").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        SwitchControllerExecutor executor = new SwitchControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(node, null));
    }
}
