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
