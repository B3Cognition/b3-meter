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

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TestPlanExecutor}.
 *
 * <p>All tests use simulation mode (no HTTP client) so no network I/O occurs.
 * The executor runs in-process on the test thread for deterministic assertions.
 */
class TestPlanExecutorTest {

    @AfterEach
    void clearRegistry() {
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TestRunContext buildContext(String runId, int virtualUsers) {
        return TestRunContext.builder()
                .runId(runId)
                .planPath("/test/plan.jmx")
                .virtualUsers(virtualUsers)
                .durationSeconds(0L)
                .uiBridge(NoOpUIBridge.INSTANCE)
                .build();
    }

    private CapturingBroker capturingBroker() {
        return new CapturingBroker();
    }

    // -------------------------------------------------------------------------
    // Default plan (empty treeData)
    // -------------------------------------------------------------------------

    @Test
    void executeDefaultPlanProducesSamples() throws InterruptedException {
        TestRunContext context = buildContext("run-default", 1);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(Map.of());

        assertTrue(executor.sampleCount() > 0,
                "Expected at least one sample result from default plan");
    }

    @Test
    void executeDefaultPlanPublishesBucketToBroker() throws InterruptedException {
        TestRunContext context = buildContext("run-broker", 1);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(null);

        assertFalse(broker.buckets().isEmpty(),
                "Expected at least one SampleBucket published to broker");
    }

    // -------------------------------------------------------------------------
    // Thread group extraction
    // -------------------------------------------------------------------------

    @Test
    void executeWithThreadGroupConfigCreatesCorrectVuCount() throws InterruptedException {
        int threads    = 3;
        int iterations = 2;

        Map<String, Object> treeData = Map.of(
                "threadGroups", List.of(
                        Map.of(
                                "threads",    threads,
                                "iterations", iterations,
                                "label",      "LoadTest",
                                "targetUrl",  "http://ignored.invalid/"
                        )
                )
        );

        TestRunContext context = buildContext("run-vus", threads);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(treeData);

        int expectedSamples = threads * iterations;
        assertEquals(expectedSamples, executor.sampleCount(),
                "Expected " + expectedSamples + " samples (" + threads + " VUs × " + iterations + " iterations)");
    }

    @Test
    void executeWithExplicitLabelPublishesBucketWithThatLabel() throws InterruptedException {
        String expectedLabel = "MyCustomSampler";
        Map<String, Object> treeData = Map.of(
                "threadGroups", List.of(
                        Map.of("threads", 1, "iterations", 1, "label", expectedLabel,
                               "targetUrl", "http://ignored.invalid/")
                )
        );

        TestRunContext context = buildContext("run-label", 1);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(treeData);

        boolean hasExpectedLabel = broker.buckets().stream()
                .anyMatch(b -> expectedLabel.equals(b.samplerLabel()));
        assertTrue(hasExpectedLabel,
                "Expected a bucket with samplerLabel='" + expectedLabel + "'");
    }

    @Test
    void executeWithMultipleThreadGroupsProducesCorrectTotals() throws InterruptedException {
        Map<String, Object> treeData = Map.of(
                "threadGroups", List.of(
                        Map.of("threads", 2, "iterations", 3, "label", "Group1",
                               "targetUrl", "http://ignored.invalid/"),
                        Map.of("threads", 1, "iterations", 2, "label", "Group2",
                               "targetUrl", "http://ignored.invalid/")
                )
        );

        TestRunContext context = buildContext("run-multi-tg", 2);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(treeData);

        int expectedSamples = 2 * 3 + 1 * 2; // 8
        assertEquals(expectedSamples, executor.sampleCount(),
                "Expected " + expectedSamples + " total samples across two thread groups");
    }

    @Test
    void executeWithEmptyThreadGroupListUsesDefault() throws InterruptedException {
        Map<String, Object> treeData = Map.of("threadGroups", List.of());

        TestRunContext context = buildContext("run-empty-tg", 1);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        executor.execute(treeData);

        assertTrue(executor.sampleCount() > 0, "Default thread group should produce samples");
    }

    // -------------------------------------------------------------------------
    // Null / edge-case treeData
    // -------------------------------------------------------------------------

    @Test
    void executeWithNullTreeDataDoesNotThrow() {
        TestRunContext context = buildContext("run-null-tree", 1);
        CapturingBroker broker = capturingBroker();

        TestPlanExecutor executor = new TestPlanExecutor(context, broker, null);
        assertDoesNotThrow(() -> executor.execute(null));
    }

    @Test
    void constructorThrowsOnNullContext() {
        CapturingBroker broker = capturingBroker();
        assertThrows(NullPointerException.class,
                () -> new TestPlanExecutor(null, broker, null));
    }

    @Test
    void constructorThrowsOnNullBroker() {
        TestRunContext context = buildContext("run-null-broker", 1);
        assertThrows(NullPointerException.class,
                () -> new TestPlanExecutor(context, null, null));
    }

    // -------------------------------------------------------------------------
    // Capturing broker helper
    // -------------------------------------------------------------------------

    /**
     * In-memory {@link com.b3meter.engine.service.SampleStreamBroker} that records
     * all published {@link SampleBucket} instances.
     */
    private static final class CapturingBroker
            implements com.b3meter.engine.service.SampleStreamBroker {

        private final CopyOnWriteArrayList<SampleBucket> captured = new CopyOnWriteArrayList<>();

        @Override
        public void subscribe(String runId, SampleBucketConsumer consumer) {
            // no-op for test
        }

        @Override
        public void unsubscribe(String runId, SampleBucketConsumer consumer) {
            // no-op for test
        }

        @Override
        public void publish(String runId, SampleBucket bucket) {
            captured.add(bucket);
        }

        List<SampleBucket> buckets() {
            return List.copyOf(captured);
        }
    }
}
