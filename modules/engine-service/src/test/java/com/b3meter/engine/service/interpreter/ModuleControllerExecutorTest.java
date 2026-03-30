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
 * Tests for {@link ModuleControllerExecutor}.
 */
class ModuleControllerExecutorTest {

    @Test
    void execute_resolvesPathAndExecutesChildren() {
        // Build a test plan tree:
        // TestPlan
        //   +- ThreadGroup "TG1"
        //       +- SimpleController "Module A"
        //           +- DebugSampler "debug1"
        //           +- DebugSampler "debug2"
        //   +- ThreadGroup "TG2"
        //       +- ModuleController -> path: [TestPlan, TG1, Module A]

        PlanNode debug1 = PlanNode.builder("DebugSampler", "debug1").build();
        PlanNode debug2 = PlanNode.builder("DebugSampler", "debug2").build();
        PlanNode moduleA = PlanNode.builder("SimpleController", "Module A")
                .child(debug1)
                .child(debug2)
                .build();
        PlanNode tg1 = PlanNode.builder("ThreadGroup", "TG1")
                .child(moduleA)
                .build();

        PlanNode moduleCtrl = PlanNode.builder("ModuleController", "Module Ref")
                .property("ModuleController.node_path",
                        List.of("Test Plan", "TG1", "Module A"))
                .build();

        PlanNode testPlan = PlanNode.builder("TestPlan", "Test Plan")
                .child(tg1)
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ModuleControllerExecutor executor = new ModuleControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(moduleCtrl, variables, testPlan);

        assertEquals(2, results.size(), "Should execute both children of Module A");
    }

    @Test
    void execute_emptyPath_returnsEmptyList() {
        PlanNode node = PlanNode.builder("ModuleController", "Empty Module")
                .build();

        PlanNode root = PlanNode.builder("TestPlan", "Test Plan").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ModuleControllerExecutor executor = new ModuleControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(node, new HashMap<>(), root);
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_invalidPath_returnsEmptyList() {
        PlanNode node = PlanNode.builder("ModuleController", "Bad Path")
                .property("ModuleController.node_path",
                        List.of("Test Plan", "NonExistent", "Controller"))
                .build();

        PlanNode root = PlanNode.builder("TestPlan", "Test Plan").build();
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ModuleControllerExecutor executor = new ModuleControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(node, new HashMap<>(), root);
        assertTrue(results.isEmpty());
    }

    @Test
    void resolveNodePath_findsDirectChild() {
        PlanNode child = PlanNode.builder("SimpleController", "Target").build();
        PlanNode root = PlanNode.builder("TestPlan", "Test Plan")
                .child(child)
                .build();

        PlanNode found = ModuleControllerExecutor.resolveNodePath(root,
                List.of("Test Plan", "Target"));
        assertNotNull(found);
        assertEquals("Target", found.getTestName());
    }

    @Test
    void resolveNodePath_nullRoot_returnsNull() {
        assertNull(ModuleControllerExecutor.resolveNodePath(null, List.of("Test Plan")));
    }

    @Test
    void constructor_nullInterpreter_throws() {
        assertThrows(NullPointerException.class,
                () -> new ModuleControllerExecutor(null));
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        ModuleControllerExecutor executor = new ModuleControllerExecutor(interpreter);
        PlanNode root = PlanNode.builder("TestPlan", "Test Plan").build();
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>(), root));
    }
}
