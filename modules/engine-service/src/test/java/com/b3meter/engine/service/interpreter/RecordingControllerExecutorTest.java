package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RecordingControllerExecutor}.
 */
class RecordingControllerExecutorTest {

    @Test
    void execute_runsAllChildren() {
        PlanNode sampler1 = PlanNode.builder("DebugSampler", "debug1").build();
        PlanNode sampler2 = PlanNode.builder("DebugSampler", "debug2").build();

        PlanNode recording = PlanNode.builder("RecordingController", "Recording")
                .child(sampler1)
                .child(sampler2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RecordingControllerExecutor executor = new RecordingControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(recording, variables);

        assertEquals(2, results.size(), "Should execute both children");
    }

    @Test
    void execute_noChildren_returnsEmptyList() {
        PlanNode recording = PlanNode.builder("RecordingController", "Empty Recording")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RecordingControllerExecutor executor = new RecordingControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(recording, new HashMap<>());

        assertTrue(results.isEmpty(), "No children means no results");
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RecordingControllerExecutor executor = new RecordingControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("RecordingController", "test").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RecordingControllerExecutor executor = new RecordingControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(node, null));
    }

    @Test
    void constructor_nullInterpreter_throws() {
        assertThrows(NullPointerException.class,
                () -> new RecordingControllerExecutor(null));
    }
}
