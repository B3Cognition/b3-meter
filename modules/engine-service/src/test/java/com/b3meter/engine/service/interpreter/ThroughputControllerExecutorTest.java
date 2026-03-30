package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ThroughputControllerExecutor}.
 */
class ThroughputControllerExecutorTest {

    @BeforeEach
    void resetCounters() {
        ThroughputControllerExecutor.resetCounters();
    }

    @Test
    void totalMode_executesExactlyNTimes() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode controller = PlanNode.builder("ThroughputController", "Total 2")
                .property("ThroughputController.style", 1)       // Total Executions
                .property("ThroughputController.maxThroughput", 2)
                .property("ThroughputController.perThread", false)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ThroughputControllerExecutor executor = new ThroughputControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        int totalExecutions = 0;
        for (int i = 0; i < 5; i++) {
            List<SampleResult> results = executor.execute(controller, variables);
            totalExecutions += results.size();
        }

        assertEquals(2, totalExecutions, "Total mode should execute children exactly 2 times");
    }

    @Test
    void percentMode_100percent_alwaysExecutes() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode controller = PlanNode.builder("ThroughputController", "All")
                .property("ThroughputController.style", 0)       // Percent Executions
                .property("ThroughputController.percentThroughput", 100.0)
                .property("ThroughputController.perThread", false)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ThroughputControllerExecutor executor = new ThroughputControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        int totalExecutions = 0;
        for (int i = 0; i < 10; i++) {
            List<SampleResult> results = executor.execute(controller, variables);
            totalExecutions += results.size();
        }

        assertEquals(10, totalExecutions, "100% should execute every time");
    }

    @Test
    void percentMode_50percent_executesApproximatelyHalf() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode controller = PlanNode.builder("ThroughputController", "Half")
                .property("ThroughputController.style", 0)
                .property("ThroughputController.percentThroughput", 50.0)
                .property("ThroughputController.perThread", false)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ThroughputControllerExecutor executor = new ThroughputControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        int totalExecutions = 0;
        for (int i = 0; i < 10; i++) {
            List<SampleResult> results = executor.execute(controller, variables);
            totalExecutions += results.size();
        }

        assertEquals(5, totalExecutions, "50% of 10 invocations should yield 5 executions");
    }

    @Test
    void totalMode_perThread_tracksPerThread() {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode controller = PlanNode.builder("ThroughputController", "PerThread")
                .property("ThroughputController.style", 1)
                .property("ThroughputController.maxThroughput", 1)
                .property("ThroughputController.perThread", true)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ThroughputControllerExecutor executor = new ThroughputControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        // First call should execute
        List<SampleResult> r1 = executor.execute(controller, variables);
        assertEquals(1, r1.size(), "First call should execute");

        // Second call should not execute (max 1 per thread)
        List<SampleResult> r2 = executor.execute(controller, variables);
        assertTrue(r2.isEmpty(), "Second call should be skipped (max 1 per thread)");
    }

    @Test
    void nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ThroughputControllerExecutor executor = new ThroughputControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }
}
