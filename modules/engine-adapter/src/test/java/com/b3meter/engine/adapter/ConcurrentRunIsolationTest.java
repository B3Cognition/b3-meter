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
package com.b3meter.engine.adapter;

import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: verifies that 10 concurrent {@link TestRunContext} instances
 * maintain fully independent state when registered in the {@link TestRunContextRegistry}.
 *
 * <p>This test exercises the removal of global statics that was the root cause of
 * cross-run state contamination in legacy JMeter when multiple runs executed
 * concurrently.
 */
class ConcurrentRunIsolationTest {

    private static final int RUN_COUNT = 10;

    @AfterEach
    void cleanUp() {
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // Independent resultWriters maps
    // -------------------------------------------------------------------------

    @Test
    void eachContextHasIndependentResultWritersMap() throws Exception {
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);

        // Add a writer to each context keyed by its own runId
        for (TestRunContext ctx : contexts) {
            ctx.putResultWriter(ctx.getRunId() + ".jtl", () -> {});
        }

        for (TestRunContext ctx : contexts) {
            // Each context has exactly one writer (its own)
            assertEquals(1, ctx.resultWriterCount(),
                    "context " + ctx.getRunId() + " should have exactly 1 writer");
            // And it is the correct one
            assertNotNull(ctx.getResultWriter(ctx.getRunId() + ".jtl"),
                    "context " + ctx.getRunId() + " should find its own writer");
        }
    }

    @Test
    void addingWriterToOneContextDoesNotAffectOthers() throws Exception {
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);

        // Add writers to the first context only
        contexts.get(0).putResultWriter("shared-key.jtl", () -> {});

        // All other contexts should see zero writers
        for (int i = 1; i < RUN_COUNT; i++) {
            assertEquals(0, contexts.get(i).resultWriterCount(),
                    "context " + contexts.get(i).getRunId()
                            + " should have no writers after first context was modified");
        }
    }

    // -------------------------------------------------------------------------
    // Independent runProperties
    // -------------------------------------------------------------------------

    @Test
    void eachContextHasIndependentRunProperties() throws Exception {
        List<TestRunContext> contexts = new ArrayList<>();
        for (int i = 0; i < RUN_COUNT; i++) {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("run.index", String.valueOf(i));
            TestRunContext ctx = TestRunContext.builder()
                    .runId("props-" + i)
                    .planPath("/plans/test.jmx")
                    .uiBridge(NoOpUIBridge.INSTANCE)
                    .runProperties(props)
                    .build();
            TestRunContextRegistry.register(ctx);
            contexts.add(ctx);
        }

        for (int i = 0; i < RUN_COUNT; i++) {
            String expected = String.valueOf(i);
            String actual = contexts.get(i).getRunProperties().getProperty("run.index");
            assertEquals(expected, actual,
                    "context props-" + i + " should have run.index=" + i);
        }
    }

    // -------------------------------------------------------------------------
    // Independent runState — concurrent modifications
    // -------------------------------------------------------------------------

    @Test
    void modifyingOneContextsStateDoesNotAffectOthers() throws Exception {
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);

        // Set a common key with a run-specific value in each context
        for (int i = 0; i < RUN_COUNT; i++) {
            contexts.get(i).putState("index", i);
        }

        // Overwrite the value in the first context
        contexts.get(0).putState("index", 999);

        // All other contexts should still have their original values
        for (int i = 1; i < RUN_COUNT; i++) {
            assertEquals(i, contexts.get(i).getState("index"),
                    "context run-" + i + " state should not be affected by changes to run-0");
        }
    }

    @Test
    void concurrentStateModificationsAreIsolated() throws Exception {
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);
        CyclicBarrier barrier = new CyclicBarrier(RUN_COUNT);
        ExecutorService pool = Executors.newFixedThreadPool(RUN_COUNT);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < RUN_COUNT; i++) {
            final int index = i;
            final TestRunContext ctx = contexts.get(i);
            futures.add(pool.submit(() -> {
                try {
                    barrier.await();
                    // Each thread writes its own index to its own context
                    ctx.putState("value", index);
                    ctx.putResultWriter("file-" + index + ".jtl", () -> {});
                } catch (Exception e) {
                    fail("unexpected exception: " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Verify: each context has the value its own thread wrote
        for (int i = 0; i < RUN_COUNT; i++) {
            TestRunContext ctx = TestRunContextRegistry.get("run-" + i);
            assertNotNull(ctx);
            assertEquals(i, ctx.getState("value"),
                    "context run-" + i + " should have value=" + i);
            assertEquals(1, ctx.resultWriterCount(),
                    "context run-" + i + " should have exactly 1 writer");
            assertNotNull(ctx.getResultWriter("file-" + i + ".jtl"),
                    "context run-" + i + " should find its own writer");
        }
    }

    // -------------------------------------------------------------------------
    // Context removal — state no longer accessible
    // -------------------------------------------------------------------------

    @Test
    void afterRemovingContextItsStateIsNoLongerAccessibleViaRegistry() throws Exception {
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);

        // Populate each context with state
        for (int i = 0; i < RUN_COUNT; i++) {
            contexts.get(i).putState("alive", true);
        }

        // Remove half of the contexts
        for (int i = 0; i < RUN_COUNT / 2; i++) {
            TestRunContextRegistry.remove("run-" + i);
        }

        // Removed contexts should not be accessible
        for (int i = 0; i < RUN_COUNT / 2; i++) {
            assertNull(TestRunContextRegistry.get("run-" + i),
                    "removed context run-" + i + " should not be in registry");
        }

        // Remaining contexts should still be accessible with their original state
        for (int i = RUN_COUNT / 2; i < RUN_COUNT; i++) {
            TestRunContext ctx = TestRunContextRegistry.get("run-" + i);
            assertNotNull(ctx, "context run-" + i + " should still be in registry");
            assertEquals(true, ctx.getState("alive"),
                    "context run-" + i + " should retain its state after peers were removed");
        }
    }

    @Test
    void activeRunCountAccurateAfterConcurrentRegistrationsAndRemovals() throws Exception {
        // Register all 10 contexts
        List<TestRunContext> contexts = createAndRegisterContexts(RUN_COUNT);
        assertEquals(RUN_COUNT, TestRunContextRegistry.activeRunCount());

        // Remove them one by one and verify count decrements
        for (int i = 0; i < RUN_COUNT; i++) {
            TestRunContextRegistry.remove(contexts.get(i).getRunId());
            assertEquals(RUN_COUNT - i - 1, TestRunContextRegistry.activeRunCount(),
                    "count should be " + (RUN_COUNT - i - 1) + " after removing " + (i + 1) + " contexts");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<TestRunContext> createAndRegisterContexts(int count) {
        List<TestRunContext> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestRunContext ctx = TestRunContext.builder()
                    .runId("run-" + i)
                    .planPath("/plans/test.jmx")
                    .uiBridge(NoOpUIBridge.INSTANCE)
                    .build();
            TestRunContextRegistry.register(ctx);
            list.add(ctx);
        }
        return list;
    }
}
