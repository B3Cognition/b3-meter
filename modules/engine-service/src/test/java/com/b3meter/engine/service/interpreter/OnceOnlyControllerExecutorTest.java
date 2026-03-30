package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OnceOnlyControllerExecutor}.
 */
class OnceOnlyControllerExecutorTest {

    @Test
    void execute_firstCall_executesChildren() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode onceOnly = PlanNode.builder("OnceOnlyController", "Login Once")
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(onceOnly, variables);

        assertEquals(1, results.size(), "First call should execute children");
    }

    @Test
    void execute_secondCall_skipsChildren() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode onceOnly = PlanNode.builder("OnceOnlyController", "Login Once")
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        // First call executes
        executor.execute(onceOnly, variables);

        // Second call should skip
        List<SampleResult> results2 = executor.execute(onceOnly, variables);
        assertTrue(results2.isEmpty(), "Second call should skip children");
    }

    @Test
    void execute_separateInstances_trackIndependently() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode once1 = PlanNode.builder("OnceOnlyController", "Setup A")
                .child(debugSampler)
                .build();

        PlanNode once2 = PlanNode.builder("OnceOnlyController", "Setup B")
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        // Execute A
        executor.execute(once1, variables);
        // Execute B — should still run (different controller name)
        List<SampleResult> resultsB = executor.execute(once2, variables);

        assertEquals(1, resultsB.size(),
                "Different OnceOnlyController instances should track independently");
    }

    @Test
    void execute_differentVariableMaps_trackSeparately() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode onceOnly = PlanNode.builder("OnceOnlyController", "Login")
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);

        // Different variable maps simulate different VU threads
        Map<String, String> vu1Vars = new HashMap<>();
        Map<String, String> vu2Vars = new HashMap<>();

        List<SampleResult> r1 = executor.execute(onceOnly, vu1Vars);
        List<SampleResult> r2 = executor.execute(onceOnly, vu2Vars);

        assertEquals(1, r1.size(), "VU1 first call should execute");
        assertEquals(1, r2.size(), "VU2 first call should execute (different variable scope)");
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode onceOnly = PlanNode.builder("OnceOnlyController", "test").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        OnceOnlyControllerExecutor executor = new OnceOnlyControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(onceOnly, null));
    }
}
