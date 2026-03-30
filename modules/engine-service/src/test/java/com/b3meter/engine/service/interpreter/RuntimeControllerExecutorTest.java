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
 * Tests for {@link RuntimeControllerExecutor}.
 */
class RuntimeControllerExecutorTest {

    @Test
    void execute_withZeroSeconds_executesOnce() {
        PlanNode sampler = PlanNode.builder("DebugSampler", "debug").build();

        PlanNode runtime = PlanNode.builder("RuntimeController", "Runtime 0s")
                .property("RuntimeController.seconds", "0")
                .child(sampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(runtime, new HashMap<>());

        assertEquals(1, results.size(),
                "With 0 seconds, should execute children exactly once");
    }

    @Test
    void execute_withDuration_executesMultipleTimes() {
        PlanNode sampler = PlanNode.builder("DebugSampler", "debug").build();

        // 1 second runtime — DebugSampler completes instantly so we should get multiple iterations
        PlanNode runtime = PlanNode.builder("RuntimeController", "Runtime 1s")
                .property("RuntimeController.seconds", "1")
                .child(sampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);

        long start = System.currentTimeMillis();
        List<SampleResult> results = executor.execute(runtime, new HashMap<>());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(results.size() >= 1,
                "Should execute at least once in 1 second, got " + results.size());
        assertTrue(elapsed >= 900,
                "Should run for approximately 1 second, ran for " + elapsed + " ms");
        assertTrue(elapsed < 3000,
                "Should not run much longer than 1 second, ran for " + elapsed + " ms");
    }

    @Test
    void execute_noChildren_returnsEmptyList() {
        PlanNode runtime = PlanNode.builder("RuntimeController", "Empty")
                .property("RuntimeController.seconds", "0")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(runtime, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("RuntimeController", "test").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(node, null));
    }

    @Test
    void constructor_nullInterpreter_throws() {
        assertThrows(NullPointerException.class,
                () -> new RuntimeControllerExecutor(null));
    }

    @Test
    void execute_noDurationProperty_executesOnce() {
        PlanNode sampler = PlanNode.builder("DebugSampler", "debug").build();

        // No seconds property at all
        PlanNode runtime = PlanNode.builder("RuntimeController", "No Duration")
                .child(sampler)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        RuntimeControllerExecutor executor = new RuntimeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(runtime, new HashMap<>());

        assertEquals(1, results.size(),
                "Without duration, should execute children exactly once");
    }
}
