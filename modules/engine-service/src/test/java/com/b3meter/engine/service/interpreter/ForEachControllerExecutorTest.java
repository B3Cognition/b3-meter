/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForEachControllerExecutor}.
 */
class ForEachControllerExecutorTest {

    @Test
    void execute_iteratesOverIndexedVariables() {
        // Set up variables: items_1, items_2, items_3
        Map<String, String> variables = new HashMap<>();
        variables.put("items_1", "alpha");
        variables.put("items_2", "beta");
        variables.put("items_3", "gamma");

        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug")
                .build();

        PlanNode forEachNode = PlanNode.builder("ForeachController", "ForEach Items")
                .property("ForeachController.inputVal", "items")
                .property("ForeachController.returnVal", "currentItem")
                .property("ForeachController.startIndex", 0)
                .property("ForeachController.endIndex", 0)
                .property("ForeachController.useSeparator", true)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, variables);

        // Should produce 3 samples (one per iteration)
        assertEquals(3, results.size(), "Should iterate 3 times for items_1, items_2, items_3");

        // After iteration, currentItem should have the last value
        assertEquals("gamma", variables.get("currentItem"),
                "returnVal should contain the last iterated value");
    }

    @Test
    void execute_stopsWhenVariableNotFound() {
        Map<String, String> variables = new HashMap<>();
        variables.put("v_1", "first");
        variables.put("v_2", "second");
        // No v_3 — iteration should stop

        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode forEachNode = PlanNode.builder("ForeachController", "ForEach V")
                .property("ForeachController.inputVal", "v")
                .property("ForeachController.returnVal", "current")
                .property("ForeachController.useSeparator", true)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, variables);

        assertEquals(2, results.size(), "Should iterate exactly 2 times");
    }

    @Test
    void execute_noSeparator() {
        Map<String, String> variables = new HashMap<>();
        variables.put("x1", "one");
        variables.put("x2", "two");

        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode forEachNode = PlanNode.builder("ForeachController", "ForEach No Sep")
                .property("ForeachController.inputVal", "x")
                .property("ForeachController.returnVal", "out")
                .property("ForeachController.useSeparator", false)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, variables);

        assertEquals(2, results.size(), "Should find x1, x2 without separator");
    }

    @Test
    void execute_endIndex_limitsIteration() {
        Map<String, String> variables = new HashMap<>();
        variables.put("d_1", "a");
        variables.put("d_2", "b");
        variables.put("d_3", "c");

        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode forEachNode = PlanNode.builder("ForeachController", "ForEach Limited")
                .property("ForeachController.inputVal", "d")
                .property("ForeachController.returnVal", "out")
                .property("ForeachController.endIndex", 2) // exclusive: stop after d_2
                .property("ForeachController.useSeparator", true)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, variables);

        assertEquals(2, results.size(), "Should iterate only 2 times (endIndex=2)");
    }

    @Test
    void execute_emptyInput_returnsEmpty() {
        Map<String, String> variables = new HashMap<>();

        PlanNode debugSampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode forEachNode = PlanNode.builder("ForeachController", "ForEach Empty")
                .property("ForeachController.inputVal", "missing")
                .property("ForeachController.returnVal", "out")
                .property("ForeachController.useSeparator", true)
                .child(debugSampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, variables);

        assertTrue(results.isEmpty(), "No matching variables should produce 0 results");
    }

    @Test
    void execute_blankInputVal_returnsEmpty() {
        PlanNode forEachNode = PlanNode.builder("ForeachController", "Bad config")
                .property("ForeachController.inputVal", "")
                .property("ForeachController.returnVal", "out")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(forEachNode, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ForEachControllerExecutor executor = new ForEachControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }
}
