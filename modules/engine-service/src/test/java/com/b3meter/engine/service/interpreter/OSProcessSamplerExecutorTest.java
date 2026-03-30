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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OSProcessSamplerExecutor}.
 *
 * <p>Uses simple OS commands ({@code echo}, {@code cat}) that are available on
 * Unix/macOS. Windows-specific tests are excluded via {@link DisabledOnOs}.
 */
class OSProcessSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("os-test");
        assertThrows(NullPointerException.class,
                () -> OSProcessSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = cmdNode("echo", "hello");
        assertThrows(NullPointerException.class,
                () -> OSProcessSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = cmdNode("echo", "hello");
        SampleResult result = new SampleResult("os-test");
        assertThrows(NullPointerException.class,
                () -> OSProcessSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty command
    // =========================================================================

    @Test
    void execute_failsOnEmptyCommand() {
        PlanNode node = cmdNode("", "");
        SampleResult result = new SampleResult("os-empty");

        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("command is empty"));
    }

    // =========================================================================
    // Command execution
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_runsEchoCommand() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "echo-test")
                .property("OSProcessSampler.command", "/bin/echo")
                .property("OSProcessSampler.arguments", "hello world")
                .build();

        SampleResult result = new SampleResult("echo-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), "echo should succeed: " + result.getFailureMessage());
        assertEquals(0, result.getStatusCode());
        assertTrue(result.getResponseBody().trim().contains("hello world"));
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_capturesMultilineOutput() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "printf-test")
                .property("OSProcessSampler.command", "/bin/sh")
                .property("OSProcessSampler.arguments", "-c\necho 'line1'; echo 'line2'")
                .build();

        SampleResult result = new SampleResult("printf-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("line1"));
        assertTrue(result.getResponseBody().contains("line2"));
    }

    // =========================================================================
    // Exit code checking
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_detectsNonZeroExitCode() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "exit-test")
                .property("OSProcessSampler.command", "/bin/sh")
                .property("OSProcessSampler.arguments", "-c\nexit 42")
                .build();

        SampleResult result = new SampleResult("exit-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(42, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("exit code 42"));
        assertTrue(result.getFailureMessage().contains("expected 0"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_customExpectedReturnCode() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "custom-exit")
                .property("OSProcessSampler.command", "/bin/sh")
                .property("OSProcessSampler.arguments", "-c\nexit 1")
                .property("OSProcessSampler.expectedReturnCode", 1)
                .build();

        SampleResult result = new SampleResult("custom-exit");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), "Exit code 1 should match expected: " + result.getFailureMessage());
        assertEquals(1, result.getStatusCode());
    }

    // =========================================================================
    // Timeout
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_timesOutLongRunningProcess() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "timeout-test")
                .property("OSProcessSampler.command", "/bin/sleep")
                .property("OSProcessSampler.arguments", "30")
                .property("OSProcessSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("timeout-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("timed out"));
    }

    // =========================================================================
    // Environment variables
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_setsEnvironmentVariables() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "env-test")
                .property("OSProcessSampler.command", "/bin/sh")
                .property("OSProcessSampler.arguments", "-c\necho $MY_VAR")
                .property("OSProcessSampler.environment", "MY_VAR=test_value")
                .build();

        SampleResult result = new SampleResult("env-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().trim().contains("test_value"));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_resolvesVariablesInCommand() {
        Map<String, String> vars = new HashMap<>();
        vars.put("msg", "resolved");

        PlanNode node = PlanNode.builder("OSProcessSampler", "var-test")
                .property("OSProcessSampler.command", "/bin/echo")
                .property("OSProcessSampler.arguments", "${msg}")
                .build();

        SampleResult result = new SampleResult("var-test");
        OSProcessSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().trim().contains("resolved"));
    }

    // =========================================================================
    // Invalid command
    // =========================================================================

    @Test
    void execute_handlesInvalidCommand() {
        PlanNode node = cmdNode("/nonexistent/binary/xyz", "");
        SampleResult result = new SampleResult("invalid-cmd");

        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
    }

    // =========================================================================
    // Stderr capture
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_capturesStderr() {
        PlanNode node = PlanNode.builder("OSProcessSampler", "stderr-test")
                .property("OSProcessSampler.command", "/bin/sh")
                .property("OSProcessSampler.arguments", "-c\necho 'stdout msg'; echo 'stderr msg' >&2")
                .build();

        SampleResult result = new SampleResult("stderr-test");
        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertTrue(result.getResponseBody().contains("stdout msg"));
        assertTrue(result.getResponseBody().contains("stderr msg"));
        assertTrue(result.getResponseBody().contains("STDERR"));
    }

    // =========================================================================
    // Timing
    // =========================================================================

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void execute_recordsTotalTime() {
        PlanNode node = cmdNode("/bin/echo", "timing");
        SampleResult result = new SampleResult("timing-test");

        OSProcessSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode cmdNode(String command, String arguments) {
        return PlanNode.builder("OSProcessSampler", "os-test")
                .property("OSProcessSampler.command", command)
                .property("OSProcessSampler.arguments", arguments)
                .build();
    }
}
