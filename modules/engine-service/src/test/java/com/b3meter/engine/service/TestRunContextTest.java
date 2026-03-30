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
package com.b3meter.engine.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TestRunContext}.
 *
 * Verifies the builder contract, immutability guarantees, validation rules,
 * and the per-run mutable state accessors added in T006.
 */
class TestRunContextTest {

    // -------------------------------------------------------------------------
    // Minimal UIBridge stub for tests — cannot use NoOpUIBridge (wrong module)
    // -------------------------------------------------------------------------

    private static final UIBridge NO_OP = new UIBridge() {
        @Override public void onTestStarted(TestRunContext c) {}
        @Override public void onSample(TestRunContext c, double s, double e) {}
        @Override public void onTestEnded(TestRunContext c) {}
        @Override public void onThreadStarted(TestRunContext c, String t, int n) {}
        @Override public void onThreadFinished(TestRunContext c, String t, int n) {}
        @Override public void reportError(String m, String t) {}
        @Override public void reportInfo(String m, String t) {}
        @Override public void refreshUI() {}
        @Override public String promptPassword(String m) { return null; }
        @Override public void onSampleReceived(TestRunContext c, String l, long e, boolean s) {}
    };

    // -------------------------------------------------------------------------
    // Builder — required fields
    // -------------------------------------------------------------------------

    @Test
    void buildsWithRequiredFields() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-001")
                .planPath("/plans/smoke.jmx")
                .uiBridge(NO_OP)
                .build();

        assertEquals("run-001", ctx.getRunId());
        assertEquals("/plans/smoke.jmx", ctx.getPlanPath());
        assertEquals(1, ctx.getVirtualUsers());      // default
        assertEquals(0L, ctx.getDurationSeconds());   // default
    }

    @Test
    void buildsWithAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Properties props = new Properties();
        props.setProperty("foo", "bar");

        TestRunContext ctx = TestRunContext.builder()
                .runId("run-002")
                .planPath("/plans/load.jmx")
                .virtualUsers(100)
                .durationSeconds(300L)
                .uiBridge(NO_OP)
                .runProperties(props)
                .startedAt(now)
                .build();

        assertEquals("run-002", ctx.getRunId());
        assertEquals("/plans/load.jmx", ctx.getPlanPath());
        assertEquals(100, ctx.getVirtualUsers());
        assertEquals(300L, ctx.getDurationSeconds());
        assertSame(NO_OP, ctx.getUiBridge());
        assertEquals("bar", ctx.getRunProperties().getProperty("foo"));
        assertEquals(now, ctx.getStartedAt());
    }

    // -------------------------------------------------------------------------
    // Builder — validation
    // -------------------------------------------------------------------------

    @Test
    void rejectsNullRunId() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                TestRunContext.builder()
                        .planPath("/plans/smoke.jmx")
                        .uiBridge(NO_OP)
                        .build()
        );
        assertTrue(ex.getMessage().contains("runId"), "error message should mention field name");
    }

    @Test
    void rejectsNullPlanPath() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                TestRunContext.builder()
                        .runId("run-003")
                        .uiBridge(NO_OP)
                        .build()
        );
        assertTrue(ex.getMessage().contains("planPath"), "error message should mention field name");
    }

    @Test
    void rejectsNullUiBridge() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                TestRunContext.builder()
                        .runId("run-004")
                        .planPath("/plans/smoke.jmx")
                        .build()
        );
        assertTrue(ex.getMessage().contains("uiBridge"), "error message should mention field name");
    }

    @Test
    void rejectsZeroVirtualUsers() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestRunContext.builder()
                        .runId("run-005")
                        .planPath("/plans/smoke.jmx")
                        .uiBridge(NO_OP)
                        .virtualUsers(0)
                        .build()
        );
        assertTrue(ex.getMessage().contains("virtualUsers"));
    }

    @Test
    void rejectsNegativeDuration() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestRunContext.builder()
                        .runId("run-006")
                        .planPath("/plans/smoke.jmx")
                        .uiBridge(NO_OP)
                        .durationSeconds(-1L)
                        .build()
        );
        assertTrue(ex.getMessage().contains("durationSeconds"));
    }

    // -------------------------------------------------------------------------
    // Initial state after construction
    // -------------------------------------------------------------------------

    @Test
    void initialStatusIsCreated() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-007")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertEquals(TestRunContext.TestRunStatus.CREATED, ctx.getStatus());
    }

    @Test
    void initialRunningIsFalse() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-008")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertFalse(ctx.isRunning());
    }

    @Test
    void defaultStartedAtIsSetAtConstruction() {
        Instant before = Instant.now();
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-009")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();
        Instant after = Instant.now();

        assertNotNull(ctx.getStartedAt());
        assertFalse(ctx.getStartedAt().isBefore(before));
        assertFalse(ctx.getStartedAt().isAfter(after));
    }

    // -------------------------------------------------------------------------
    // Status transitions
    // -------------------------------------------------------------------------

    @Test
    void statusTransitionsAreReflected() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-010")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        ctx.setStatus(TestRunContext.TestRunStatus.RUNNING);
        assertEquals(TestRunContext.TestRunStatus.RUNNING, ctx.getStatus());

        ctx.setStatus(TestRunContext.TestRunStatus.STOPPING);
        assertEquals(TestRunContext.TestRunStatus.STOPPING, ctx.getStatus());

        ctx.setStatus(TestRunContext.TestRunStatus.STOPPED);
        assertEquals(TestRunContext.TestRunStatus.STOPPED, ctx.getStatus());
    }

    @Test
    void setStatusRejectsNull() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-011")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertThrows(NullPointerException.class, () -> ctx.setStatus(null));
    }

    // -------------------------------------------------------------------------
    // Running flag
    // -------------------------------------------------------------------------

    @Test
    void runningFlagCanBeToggled() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-012")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        ctx.setRunning(true);
        assertTrue(ctx.isRunning());

        ctx.setRunning(false);
        assertFalse(ctx.isRunning());
    }

    // -------------------------------------------------------------------------
    // runState — general-purpose state bag
    // -------------------------------------------------------------------------

    @Test
    void runStatePutAndGetRoundTrips() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-013")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        ctx.putState("engine", "myEngine");
        assertEquals("myEngine", ctx.getState("engine"));
    }

    @Test
    void runStateGetReturnsNullForMissingKey() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-014")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertNull(ctx.getState("missing"));
    }

    @Test
    void runStateRemoveDeletesEntry() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-015")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        ctx.putState("k", "v");
        Object removed = ctx.removeState("k");

        assertEquals("v", removed);
        assertNull(ctx.getState("k"));
    }

    @Test
    void runStatePutRejectsNullKey() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-016")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertThrows(NullPointerException.class, () -> ctx.putState(null, "v"));
    }

    @Test
    void runStatePutRejectsNullValue() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-017")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertThrows(NullPointerException.class, () -> ctx.putState("k", null));
    }

    @Test
    void getRunStateReturnsUnmodifiableView() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-018")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        ctx.putState("x", 42);
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getRunState().put("y", 99));
    }

    // -------------------------------------------------------------------------
    // resultWriters — file-handle map
    // -------------------------------------------------------------------------

    @Test
    void resultWriterPutAndGetRoundTrips() throws Exception {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-019")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        AutoCloseable writer = () -> {};
        ctx.putResultWriter("/tmp/results.jtl", writer);

        assertSame(writer, ctx.getResultWriter("/tmp/results.jtl"));
        assertEquals(1, ctx.resultWriterCount());
    }

    @Test
    void resultWriterGetReturnsNullForMissingKey() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-020")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertNull(ctx.getResultWriter("does-not-exist"));
    }

    @Test
    void resultWriterRemoveDeletesEntry() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-021")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        AutoCloseable writer = () -> {};
        ctx.putResultWriter("w1", writer);
        AutoCloseable removed = ctx.removeResultWriter("w1");

        assertSame(writer, removed);
        assertEquals(0, ctx.resultWriterCount());
    }

    @Test
    void resultWriterCountTracksEntries() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-022")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertEquals(0, ctx.resultWriterCount());
        ctx.putResultWriter("a", () -> {});
        assertEquals(1, ctx.resultWriterCount());
        ctx.putResultWriter("b", () -> {});
        assertEquals(2, ctx.resultWriterCount());
        ctx.removeResultWriter("a");
        assertEquals(1, ctx.resultWriterCount());
    }

    // -------------------------------------------------------------------------
    // runProperties — defensive copy
    // -------------------------------------------------------------------------

    @Test
    void runPropertiesReturnedAsCopy() {
        Properties original = new Properties();
        original.setProperty("timeout", "5000");

        TestRunContext ctx = TestRunContext.builder()
                .runId("run-023")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .runProperties(original)
                .build();

        Properties copy = ctx.getRunProperties();
        copy.setProperty("timeout", "9999");  // mutate copy

        // internal copy should not be affected
        assertEquals("5000", ctx.getRunProperties().getProperty("timeout"));
    }

    @Test
    void runPropertiesDefaultsToEmptyWhenNotSet() {
        TestRunContext ctx = TestRunContext.builder()
                .runId("run-024")
                .planPath("/p.jmx")
                .uiBridge(NO_OP)
                .build();

        assertTrue(ctx.getRunProperties().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Equality and hash code
    // -------------------------------------------------------------------------

    @Test
    void equalityBasedOnRunIdOnly() {
        TestRunContext a = TestRunContext.builder()
                .runId("run-same")
                .planPath("/plans/a.jmx")
                .virtualUsers(10)
                .uiBridge(NO_OP)
                .build();
        TestRunContext b = TestRunContext.builder()
                .runId("run-same")
                .planPath("/plans/b.jmx")   // different plan, same runId
                .virtualUsers(50)
                .uiBridge(NO_OP)
                .build();

        assertEquals(a, b, "contexts with the same runId should be equal");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentRunIdsAreNotEqual() {
        TestRunContext a = TestRunContext.builder().runId("run-A").planPath("/p.jmx").uiBridge(NO_OP).build();
        TestRunContext b = TestRunContext.builder().runId("run-B").planPath("/p.jmx").uiBridge(NO_OP).build();

        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsRunId() {
        TestRunContext ctx = TestRunContext.builder().runId("run-str").planPath("/p.jmx").uiBridge(NO_OP).build();
        assertTrue(ctx.toString().contains("run-str"));
    }

    @Test
    void toStringContainsStatus() {
        TestRunContext ctx = TestRunContext.builder().runId("run-sts").planPath("/p.jmx").uiBridge(NO_OP).build();
        assertTrue(ctx.toString().contains("CREATED"));
    }
}
