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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RandomOrderControllerExecutor}.
 */
class RandomOrderControllerExecutorTest {

    @Test
    void execute_allChildrenExecuted() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "C").build();

        PlanNode randomOrder = PlanNode.builder("RandomOrderController", "Random Order")
                .child(child0)
                .child(child1)
                .child(child2)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(randomOrder, variables);

        assertEquals(3, results.size(), "All children should be executed");

        Set<String> labels = results.stream()
                .map(SampleResult::getLabel)
                .collect(Collectors.toSet());
        assertEquals(Set.of("A", "B", "C"), labels,
                "All three children should have produced results");
    }

    @Test
    void execute_orderVariesAcrossInvocations() {
        PlanNode child0 = PlanNode.builder("DebugSampler", "A").build();
        PlanNode child1 = PlanNode.builder("DebugSampler", "B").build();
        PlanNode child2 = PlanNode.builder("DebugSampler", "C").build();
        PlanNode child3 = PlanNode.builder("DebugSampler", "D").build();

        PlanNode randomOrder = PlanNode.builder("RandomOrderController", "Random Order")
                .child(child0)
                .child(child1)
                .child(child2)
                .child(child3)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);

        Set<String> orderings = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            List<SampleResult> results = executor.execute(randomOrder, new HashMap<>());
            String order = results.stream()
                    .map(SampleResult::getLabel)
                    .collect(Collectors.joining(","));
            orderings.add(order);
        }

        assertTrue(orderings.size() > 1,
                "Over 50 iterations with 4 children, should see different orderings");
    }

    @Test
    void execute_singleChild_executesIt() {
        PlanNode child = PlanNode.builder("DebugSampler", "Only").build();

        PlanNode randomOrder = PlanNode.builder("RandomOrderController", "Random Order")
                .child(child)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(randomOrder, new HashMap<>());
        assertEquals(1, results.size());
        assertEquals("Only", results.get(0).getLabel());
    }

    @Test
    void execute_noChildren_returnsEmpty() {
        PlanNode randomOrder = PlanNode.builder("RandomOrderController", "Empty").build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);

        assertTrue(executor.execute(randomOrder, new HashMap<>()).isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("RandomOrderController", "test").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RandomOrderControllerExecutor executor = new RandomOrderControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(node, null));
    }
}
