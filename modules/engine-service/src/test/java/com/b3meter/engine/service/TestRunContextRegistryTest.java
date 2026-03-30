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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TestRunContextRegistry}.
 *
 * Covers:
 * - Register / get / remove lifecycle
 * - Concurrent registration from 10 threads (no cross-contamination, no lost updates)
 * - activeRunCount accuracy
 * - Duplicate runId rejection (IllegalStateException)
 * - clear() for test isolation
 */
class TestRunContextRegistryTest {

    // -------------------------------------------------------------------------
    // Minimal UIBridge stub
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
    // Test isolation
    // -------------------------------------------------------------------------

    @AfterEach
    void cleanUp() {
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // Register / get / remove lifecycle
    // -------------------------------------------------------------------------

    @Test
    void registeredContextIsRetrievableByRunId() {
        TestRunContext ctx = buildContext("reg-001");

        TestRunContextRegistry.register(ctx);

        assertSame(ctx, TestRunContextRegistry.get("reg-001"));
    }

    @Test
    void registerReturnsSameContextReference() {
        TestRunContext ctx = buildContext("reg-002");

        TestRunContext returned = TestRunContextRegistry.register(ctx);

        assertSame(ctx, returned);
    }

    @Test
    void getReturnsNullForUnknownRunId() {
        assertNull(TestRunContextRegistry.get("unknown"));
    }

    @Test
    void removeReturnsPreviouslyRegisteredContext() {
        TestRunContext ctx = buildContext("reg-003");
        TestRunContextRegistry.register(ctx);

        TestRunContext removed = TestRunContextRegistry.remove("reg-003");

        assertSame(ctx, removed);
        assertNull(TestRunContextRegistry.get("reg-003"),
                "context should not be accessible after remove");
    }

    @Test
    void removeReturnsNullForUnknownRunId() {
        assertNull(TestRunContextRegistry.remove("not-registered"));
    }

    // -------------------------------------------------------------------------
    // activeRunCount accuracy
    // -------------------------------------------------------------------------

    @Test
    void activeRunCountIsZeroWhenEmpty() {
        assertEquals(0, TestRunContextRegistry.activeRunCount());
    }

    @Test
    void activeRunCountIncrementsOnRegister() {
        TestRunContextRegistry.register(buildContext("cnt-001"));
        assertEquals(1, TestRunContextRegistry.activeRunCount());

        TestRunContextRegistry.register(buildContext("cnt-002"));
        assertEquals(2, TestRunContextRegistry.activeRunCount());
    }

    @Test
    void activeRunCountDecrementsOnRemove() {
        TestRunContextRegistry.register(buildContext("cnt-003"));
        TestRunContextRegistry.register(buildContext("cnt-004"));

        TestRunContextRegistry.remove("cnt-003");

        assertEquals(1, TestRunContextRegistry.activeRunCount());
    }

    // -------------------------------------------------------------------------
    // activeRuns collection
    // -------------------------------------------------------------------------

    @Test
    void activeRunsReturnsAllRegisteredContexts() {
        TestRunContext a = buildContext("active-001");
        TestRunContext b = buildContext("active-002");
        TestRunContextRegistry.register(a);
        TestRunContextRegistry.register(b);

        Collection<TestRunContext> runs = TestRunContextRegistry.activeRuns();

        assertEquals(2, runs.size());
        assertTrue(runs.contains(a));
        assertTrue(runs.contains(b));
    }

    @Test
    void activeRunsIsUnmodifiable() {
        TestRunContext ctx = buildContext("active-003");
        TestRunContextRegistry.register(ctx);

        Collection<TestRunContext> runs = TestRunContextRegistry.activeRuns();

        assertThrows(UnsupportedOperationException.class, () -> runs.remove(ctx));
    }

    // -------------------------------------------------------------------------
    // Duplicate runId rejection
    // -------------------------------------------------------------------------

    @Test
    void duplicateRunIdRegistrationThrowsIllegalStateException() {
        TestRunContextRegistry.register(buildContext("dup-001"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TestRunContextRegistry.register(buildContext("dup-001")));

        assertTrue(ex.getMessage().contains("dup-001"),
                "exception message should include the duplicate runId");
    }

    // -------------------------------------------------------------------------
    // Null argument rejection
    // -------------------------------------------------------------------------

    @Test
    void registerRejectsNullContext() {
        assertThrows(NullPointerException.class,
                () -> TestRunContextRegistry.register(null));
    }

    @Test
    void getRejectsNullRunId() {
        assertThrows(NullPointerException.class,
                () -> TestRunContextRegistry.get(null));
    }

    @Test
    void removeRejectsNullRunId() {
        assertThrows(NullPointerException.class,
                () -> TestRunContextRegistry.remove(null));
    }

    // -------------------------------------------------------------------------
    // Concurrent registration from 10 threads
    // -------------------------------------------------------------------------

    @Test
    void concurrentRegistrationFrom10ThreadsAllSucceed() throws Exception {
        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String runId = "concurrent-" + i;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await();  // all threads start at the same moment
                    TestRunContextRegistry.register(buildContext(runId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(threadCount, successCount.get(),
                "all 10 threads should register without error");
        assertEquals(0, errorCount.get());
        assertEquals(threadCount, TestRunContextRegistry.activeRunCount());
    }

    @Test
    void concurrentRegistrationPreservesEachContextsIndependentState() throws Exception {
        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await();
                    TestRunContext ctx = buildContext("iso-" + index);
                    TestRunContextRegistry.register(ctx);
                    ctx.putState("ownIndex", index);
                } catch (Exception e) {
                    fail("unexpected exception: " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        for (int i = 0; i < threadCount; i++) {
            TestRunContext ctx = TestRunContextRegistry.get("iso-" + i);
            assertNotNull(ctx, "context for iso-" + i + " should be present");
            assertEquals(i, ctx.getState("ownIndex"),
                    "each context should retain its own state, not another's");
        }
    }

    // -------------------------------------------------------------------------
    // clear() for test isolation
    // -------------------------------------------------------------------------

    @Test
    void clearRemovesAllContexts() {
        TestRunContextRegistry.register(buildContext("clr-001"));
        TestRunContextRegistry.register(buildContext("clr-002"));

        TestRunContextRegistry.clear();

        assertEquals(0, TestRunContextRegistry.activeRunCount());
        assertNull(TestRunContextRegistry.get("clr-001"));
        assertNull(TestRunContextRegistry.get("clr-002"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TestRunContext buildContext(String runId) {
        return TestRunContext.builder()
                .runId(runId)
                .planPath("/plans/test.jmx")
                .uiBridge(NO_OP)
                .build();
    }
}
