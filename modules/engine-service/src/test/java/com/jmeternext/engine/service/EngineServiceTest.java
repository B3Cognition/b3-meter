package com.jmeternext.engine.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link EngineService} interface contract.
 *
 * <p>Because {@link EngineService} is an interface, these tests:
 * <ol>
 *   <li>Verify the interface is reifiable via a minimal anonymous implementation</li>
 *   <li>Confirm all five required methods are present with the correct signatures</li>
 *   <li>Verify the anonymous implementation can be invoked without error</li>
 * </ol>
 */
class EngineServiceTest {

    // -------------------------------------------------------------------------
    // Interface completeness — all five methods exist
    // -------------------------------------------------------------------------

    @Test
    void interfaceHasStartRunMethod() throws NoSuchMethodException {
        Method m = EngineService.class.getMethod(
                "startRun", String.class, Map.class, Properties.class);
        assertEquals(TestRunHandle.class, m.getReturnType());
    }

    @Test
    void interfaceHasStopRunMethod() throws NoSuchMethodException {
        Method m = EngineService.class.getMethod("stopRun", String.class);
        assertEquals(void.class, m.getReturnType());
    }

    @Test
    void interfaceHasStopRunNowMethod() throws NoSuchMethodException {
        Method m = EngineService.class.getMethod("stopRunNow", String.class);
        assertEquals(void.class, m.getReturnType());
    }

    @Test
    void interfaceHasGetRunStatusMethod() throws NoSuchMethodException {
        Method m = EngineService.class.getMethod("getRunStatus", String.class);
        assertEquals(TestRunContext.TestRunStatus.class, m.getReturnType());
    }

    @Test
    void interfaceHasActiveRunsMethod() throws NoSuchMethodException {
        Method m = EngineService.class.getMethod("activeRuns");
        assertEquals(Collection.class, m.getReturnType());
    }

    // -------------------------------------------------------------------------
    // Minimal stub implementation can satisfy the interface
    // -------------------------------------------------------------------------

    @Test
    void stubImplementationCanStartAndQueryRun() {
        EngineService stub = new StubEngineService();

        TestRunHandle handle = stub.startRun("plan-1", Collections.emptyMap(), new Properties());

        assertNotNull(handle);
        assertEquals("stub-run-1", handle.runId());
        assertEquals(TestRunContext.TestRunStatus.RUNNING, stub.getRunStatus("stub-run-1"));
    }

    @Test
    void stubImplementationStopRunDoesNotThrow() {
        EngineService stub = new StubEngineService();
        assertDoesNotThrow(() -> stub.stopRun("any-run"));
    }

    @Test
    void stubImplementationStopRunNowDoesNotThrow() {
        EngineService stub = new StubEngineService();
        assertDoesNotThrow(() -> stub.stopRunNow("any-run"));
    }

    @Test
    void stubImplementationActiveRunsIsNotNull() {
        EngineService stub = new StubEngineService();
        assertNotNull(stub.activeRuns());
    }

    // -------------------------------------------------------------------------
    // Minimal stub — keeps implementation local to test
    // -------------------------------------------------------------------------

    private static final class StubEngineService implements EngineService {

        @Override
        public TestRunHandle startRun(String planId, Map<String, Object> treeData, Properties overrides) {
            return new TestRunHandle("stub-run-1", Instant.now(), new CompletableFuture<>());
        }

        @Override
        public void stopRun(String runId) {
            // stub — intentionally no-op
        }

        @Override
        public void stopRunNow(String runId) {
            // stub — intentionally no-op
        }

        @Override
        public TestRunContext.TestRunStatus getRunStatus(String runId) {
            return TestRunContext.TestRunStatus.RUNNING;
        }

        @Override
        public Collection<TestRunContext> activeRuns() {
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // No-run result: error percent convenience
    // -------------------------------------------------------------------------

    @Test
    void testRunResultWithZeroSamplesReturnsZeroErrorPercent() {
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.STOPPED,
                Instant.now(), Instant.now().plusSeconds(1),
                0L, 0L, Duration.ZERO);
        assertEquals(0.0, result.errorPercent(), 1e-9);
    }
}
