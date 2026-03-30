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
package com.b3meter.engine.adapter.cli;

import com.b3meter.engine.service.EngineService;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunHandle;
import com.b3meter.engine.service.TestRunResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link B3MeterCli}.
 *
 * <p>All tests use a stub {@link EngineService} injected via constructor —
 * no real JMeter engine or file I/O is required.
 */
class B3MeterCliTest {

    @TempDir
    File tempDir;

    private StringWriter outBuffer;
    private StringWriter errBuffer;

    @BeforeEach
    void setUp() {
        outBuffer = new StringWriter();
        errBuffer = new StringWriter();
    }

    private B3MeterCli cli(EngineService engineService) {
        return new B3MeterCli(
                engineService,
                new PrintWriter(outBuffer, true),
                new PrintWriter(errBuffer, true)
        );
    }

    private File validPlan() throws Exception {
        File plan = new File(tempDir, "test-plan.jmx");
        Files.writeString(plan.toPath(), "<jmeterTestPlan/>");
        return plan;
    }

    // -------------------------------------------------------------------------
    // Missing required flags → exit code 2
    // -------------------------------------------------------------------------

    @Test
    void missingTestPlanFlag_returnsExitCode2() throws Exception {
        // -n without -t
        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED)).run(new String[]{"-n"});

        assertEquals(B3MeterCli.EXIT_CONFIG_ERROR, code,
                "Missing -t should return exit code 2");
        assertTrue(errBuffer.toString().contains("-t"),
                "Error output should mention -t flag");
    }

    @Test
    void missingNonGuiFlag_returnsExitCode2() throws Exception {
        File plan = validPlan();
        // -t without -n
        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED))
                .run(new String[]{"-t", plan.getAbsolutePath()});

        assertEquals(B3MeterCli.EXIT_CONFIG_ERROR, code,
                "Missing -n should return exit code 2");
        assertTrue(errBuffer.toString().contains("-n"),
                "Error output should mention -n flag");
    }

    @Test
    void noArgs_returnsExitCode2() {
        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED)).run(new String[]{});

        assertEquals(B3MeterCli.EXIT_CONFIG_ERROR, code,
                "No arguments should return exit code 2");
    }

    // -------------------------------------------------------------------------
    // Invalid file path → exit code 2 with error message
    // -------------------------------------------------------------------------

    @Test
    void nonExistentPlanFile_returnsExitCode2WithErrorMessage() {
        File missing = new File(tempDir, "does-not-exist.jmx");

        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED))
                .run(new String[]{"-n", "-t", missing.getAbsolutePath()});

        assertEquals(B3MeterCli.EXIT_CONFIG_ERROR, code,
                "Non-existent plan file should return exit code 2");
        assertTrue(errBuffer.toString().contains("does not exist"),
                "Error output should mention the file does not exist");
    }

    @Test
    void directoryAsPlanFile_returnsExitCode2WithErrorMessage() {
        // tempDir itself is a directory, not a file
        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED))
                .run(new String[]{"-n", "-t", tempDir.getAbsolutePath()});

        assertEquals(B3MeterCli.EXIT_CONFIG_ERROR, code,
                "Directory passed as plan should return exit code 2");
        assertTrue(errBuffer.toString().contains("not a file"),
                "Error output should mention path is not a file");
    }

    // -------------------------------------------------------------------------
    // Valid plan with mocked engine → exit code 0
    // -------------------------------------------------------------------------

    @Test
    void validPlanWithNonGuiFlag_returnsExitCode0() throws Exception {
        File plan = validPlan();

        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED))
                .run(new String[]{"-n", "-t", plan.getAbsolutePath()});

        assertEquals(B3MeterCli.EXIT_OK, code,
                "-n -t with valid plan and STOPPED status should return exit code 0");
    }

    @Test
    void engineReturnsErrorStatus_returnsExitCode1() throws Exception {
        File plan = validPlan();

        int code = cli(noOpEngine(TestRunContext.TestRunStatus.ERROR))
                .run(new String[]{"-n", "-t", plan.getAbsolutePath()});

        assertEquals(B3MeterCli.EXIT_TEST_FAILURE, code,
                "Engine ERROR status should return exit code 1");
    }

    // -------------------------------------------------------------------------
    // -J property overrides are passed to engine
    // -------------------------------------------------------------------------

    @Test
    void jPropertyOverridesArePassedToEngine() throws Exception {
        File plan = validPlan();
        AtomicReference<Properties> capturedOverrides = new AtomicReference<>();

        EngineService capturingEngine = new EngineService() {
            @Override
            public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
                capturedOverrides.set(overrides);
                return completedHandle(TestRunContext.TestRunStatus.STOPPED);
            }

            @Override
            public void stopRun(String runId) {}

            @Override
            public void stopRunNow(String runId) {}

            @Override
            public TestRunContext.TestRunStatus getRunStatus(String runId) { return null; }

            @Override
            public Collection<TestRunContext> activeRuns() { return Collections.emptyList(); }
        };

        cli(capturingEngine).run(new String[]{
                "-n",
                "-t", plan.getAbsolutePath(),
                "-J", "threads=50",
                "-J", "duration=120"
        });

        assertNotNull(capturedOverrides.get(), "Overrides should have been passed to engine");
        assertEquals("50", capturedOverrides.get().getProperty("threads"),
                "threads property should be '50'");
        assertEquals("120", capturedOverrides.get().getProperty("duration"),
                "duration property should be '120'");
    }

    @Test
    void jPropertyOverridesWithNoValue_setEmptyStringValue() throws Exception {
        File plan = validPlan();
        AtomicReference<Properties> capturedOverrides = new AtomicReference<>();

        EngineService capturingEngine = capturingPropertiesEngine(capturedOverrides);

        cli(capturingEngine).run(new String[]{
                "-n",
                "-t", plan.getAbsolutePath(),
                "-J", "debugMode"
        });

        assertNotNull(capturedOverrides.get());
        assertTrue(capturedOverrides.get().containsKey("debugMode"),
                "debugMode key should be present even without a value");
        assertEquals("", capturedOverrides.get().getProperty("debugMode"),
                "debugMode value should be empty string");
    }

    // -------------------------------------------------------------------------
    // --help prints usage
    // -------------------------------------------------------------------------

    @Test
    void helpFlag_printsUsageAndReturnsExitCode0() {
        int code = cli(noOpEngine(TestRunContext.TestRunStatus.STOPPED)).run(new String[]{"--help"});

        assertEquals(B3MeterCli.EXIT_OK, code,
                "--help should return exit code 0");
        String output = outBuffer.toString();
        assertTrue(output.contains("b3meter") || output.contains("Usage"),
                "--help output should contain command name or 'Usage'");
    }

    // -------------------------------------------------------------------------
    // Engine is called with the correct plan path
    // -------------------------------------------------------------------------

    @Test
    void planIdPassedToEngineMatchesPlanFilePath() throws Exception {
        File plan = validPlan();
        AtomicReference<String> capturedPlanId = new AtomicReference<>();

        EngineService capturingEngine = new EngineService() {
            @Override
            public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
                capturedPlanId.set(planId);
                return completedHandle(TestRunContext.TestRunStatus.STOPPED);
            }

            @Override
            public void stopRun(String runId) {}

            @Override
            public void stopRunNow(String runId) {}

            @Override
            public TestRunContext.TestRunStatus getRunStatus(String runId) { return null; }

            @Override
            public Collection<TestRunContext> activeRuns() { return Collections.emptyList(); }
        };

        cli(capturingEngine).run(new String[]{"-n", "-t", plan.getAbsolutePath()});

        assertEquals(plan.getAbsolutePath(), capturedPlanId.get(),
                "Plan ID passed to engine should be the absolute path of the plan file");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static EngineService noOpEngine(TestRunContext.TestRunStatus status) {
        return new EngineService() {
            @Override
            public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
                return completedHandle(status);
            }

            @Override
            public void stopRun(String runId) {}

            @Override
            public void stopRunNow(String runId) {}

            @Override
            public TestRunContext.TestRunStatus getRunStatus(String runId) { return null; }

            @Override
            public Collection<TestRunContext> activeRuns() { return Collections.emptyList(); }
        };
    }

    private static EngineService capturingPropertiesEngine(AtomicReference<Properties> ref) {
        return new EngineService() {
            @Override
            public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
                ref.set(overrides);
                return completedHandle(TestRunContext.TestRunStatus.STOPPED);
            }

            @Override
            public void stopRun(String runId) {}

            @Override
            public void stopRunNow(String runId) {}

            @Override
            public TestRunContext.TestRunStatus getRunStatus(String runId) { return null; }

            @Override
            public Collection<TestRunContext> activeRuns() { return Collections.emptyList(); }
        };
    }

    private static TestRunHandle completedHandle(TestRunContext.TestRunStatus status) {
        String runId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        TestRunResult result = new TestRunResult(
                runId,
                status,
                now,
                now,
                0L,
                0L,
                Duration.ZERO
        );
        return new TestRunHandle(runId, now, CompletableFuture.completedFuture(result));
    }
}
