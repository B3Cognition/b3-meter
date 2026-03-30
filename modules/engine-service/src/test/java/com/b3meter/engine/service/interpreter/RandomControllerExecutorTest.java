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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RandomControllerExecutor}.
 */
class RandomControllerExecutorTest {

    @Test
    void execute_selectsExactlyOneChild() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "C").build();

        PlanNode randomNode = PlanNode.builder("RandomController", "Random")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomControllerExecutor executor = new RandomControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(randomNode, variables);

        assertEquals(1, results.size(), "Should execute exactly one child");
        String label = results.get(0).getLabel();
        assertTrue(Set.of("A", "B", "C").contains(label),
                "Selected child should be one of A, B, C");
    }

    @Test
    void execute_multipleInvocations_eventuallySelectsDifferentChildren() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "C").build();

        PlanNode randomNode = PlanNode.builder("RandomController", "Random")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomControllerExecutor executor = new RandomControllerExecutor(interpreter);

        Set<String> selectedLabels = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            List<SampleResult> results = executor.execute(randomNode, new HashMap<>());
            if (!results.isEmpty()) {
                selectedLabels.add(results.get(0).getLabel());
            }
        }

        assertTrue(selectedLabels.size() > 1,
                "Over 100 iterations, should select more than one distinct child");
    }

    @Test
    void execute_singleChild_alwaysSelectsThatChild() {
        PlanNode child = PlanNode.builder("DebugSampler", "Only").build();

        PlanNode randomNode = PlanNode.builder("RandomController", "Random")
                .child(child)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomControllerExecutor executor = new RandomControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(randomNode, new HashMap<>());

        assertEquals(1, results.size());
        assertEquals("Only", results.get(0).getLabel());
    }

    @Test
    void execute_noChildren_returnsEmpty() {
        PlanNode randomNode = PlanNode.builder("RandomController", "Empty").build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomControllerExecutor executor = new RandomControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(randomNode, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomControllerExecutor executor = new RandomControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }
}
