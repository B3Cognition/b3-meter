package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InterleaveControllerExecutor}.
 */
class InterleaveControllerExecutorTest {

    @AfterEach
    void cleanup() {
        InterleaveControllerExecutor.resetCounters();
    }

    @Test
    void execute_cyclesThroughChildrenInOrder() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "C").build();

        PlanNode interleave = PlanNode.builder("InterleaveControl", "Interleave")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        InterleaveControllerExecutor executor = new InterleaveControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        // First call → A
        List<SampleResult> r1 = executor.execute(interleave, variables);
        assertEquals("A", r1.get(0).getLabel());

        // Second call → B
        List<SampleResult> r2 = executor.execute(interleave, variables);
        assertEquals("B", r2.get(0).getLabel());

        // Third call → C
        List<SampleResult> r3 = executor.execute(interleave, variables);
        assertEquals("C", r3.get(0).getLabel());

        // Fourth call → wraps back to A
        List<SampleResult> r4 = executor.execute(interleave, variables);
        assertEquals("A", r4.get(0).getLabel());
    }

    @Test
    void execute_perVu_differentVariableMapsTrackSeparately() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "X").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "Y").build();

        PlanNode interleave = PlanNode.builder("InterleaveControl", "Interleave")
                .child(child0)
                .child(child1)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        InterleaveControllerExecutor executor = new InterleaveControllerExecutor(interpreter);

        Map<String, String> vu1 = new HashMap<>();
        Map<String, String> vu2 = new HashMap<>();

        // VU1: first → X
        assertEquals("X", executor.execute(interleave, vu1).get(0).getLabel());
        // VU2: first → X (independent counter)
        assertEquals("X", executor.execute(interleave, vu2).get(0).getLabel());
        // VU1: second → Y
        assertEquals("Y", executor.execute(interleave, vu1).get(0).getLabel());
    }

    @Test
    void execute_accrossThreads_sharedCounter() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "X").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "Y").build();

        PlanNode interleave = PlanNode.builder("InterleaveControl", "Shared Interleave")
                .property("InterleaveControl.accrossThreads", true)
                .child(child0)
                .child(child1)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        InterleaveControllerExecutor executor = new InterleaveControllerExecutor(interpreter);

        Map<String, String> vu1 = new HashMap<>();
        Map<String, String> vu2 = new HashMap<>();

        // Both VUs share the same counter
        assertEquals("X", executor.execute(interleave, vu1).get(0).getLabel());
        assertEquals("Y", executor.execute(interleave, vu2).get(0).getLabel());
        assertEquals("X", executor.execute(interleave, vu1).get(0).getLabel());
    }

    @Test
    void execute_noChildren_returnsEmpty() {
        PlanNode interleave = PlanNode.builder("InterleaveControl", "Empty").build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        InterleaveControllerExecutor executor = new InterleaveControllerExecutor(interpreter);

        assertTrue(executor.execute(interleave, new HashMap<>()).isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        InterleaveControllerExecutor executor = new InterleaveControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }
}
