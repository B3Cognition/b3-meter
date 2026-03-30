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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IncludeControllerExecutor}.
 */
class IncludeControllerExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void execute_emptyPath_returnsEmptyList() {
        PlanNode node = PlanNode.builder("IncludeController", "Include")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IncludeControllerExecutor executor = new IncludeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(node, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_nonExistentFile_returnsEmptyList() {
        PlanNode node = PlanNode.builder("IncludeController", "Include")
                .property("IncludeController.includePath", "/no/such/file.jmx")
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IncludeControllerExecutor executor = new IncludeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(node, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void execute_validJmxFile_executesChildren() throws IOException {
        // Create a minimal JMX file with a DebugSampler
        String jmxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Included Plan">
                    </TestPlan>
                    <hashTree>
                      <DebugSampler guiclass="TestBeanGUI" testclass="DebugSampler" testname="Included Debug">
                      </DebugSampler>
                      <hashTree/>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        Path jmxFile = tempDir.resolve("included.jmx");
        Files.writeString(jmxFile, jmxContent);

        PlanNode node = PlanNode.builder("IncludeController", "Include")
                .property("IncludeController.includePath", jmxFile.toString())
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IncludeControllerExecutor executor = new IncludeControllerExecutor(interpreter);
        Map<String, String> variables = new HashMap<>();

        List<SampleResult> results = executor.execute(node, variables);

        assertEquals(1, results.size(), "Should execute the included DebugSampler");
    }

    @Test
    void execute_malformedJmx_returnsEmptyList() throws IOException {
        Path jmxFile = tempDir.resolve("bad.jmx");
        Files.writeString(jmxFile, "this is not valid XML");

        PlanNode node = PlanNode.builder("IncludeController", "Include")
                .property("IncludeController.includePath", jmxFile.toString())
                .build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IncludeControllerExecutor executor = new IncludeControllerExecutor(interpreter);

        List<SampleResult> results = executor.execute(node, new HashMap<>());
        assertTrue(results.isEmpty());
    }

    @Test
    void constructor_nullInterpreter_throws() {
        assertThrows(NullPointerException.class,
                () -> new IncludeControllerExecutor(null));
    }

    @Test
    void execute_nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        IncludeControllerExecutor executor = new IncludeControllerExecutor(interpreter);
        assertThrows(NullPointerException.class,
                () -> executor.execute(null, new HashMap<>()));
    }
}
