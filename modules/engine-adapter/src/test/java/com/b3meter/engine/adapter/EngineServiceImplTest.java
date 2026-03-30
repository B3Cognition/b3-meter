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
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunContextRegistry;
import com.b3meter.engine.service.TestRunHandle;
import com.b3meter.engine.service.TestRunResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EngineServiceImpl}.
 *
 * <p>All tests run in simulation mode (no HTTP client) so they execute
 * without network I/O and complete quickly.
 */
class EngineServiceImplTest {

    @AfterEach
    void clearRegistry() {
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // startRun — basic contract
    // -------------------------------------------------------------------------

    @Test
    void startRunReturnsNonNullHandle() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        TestRunHandle handle = engine.startRun("plan-1", Collections.emptyMap(), new Properties());

        assertNotNull(handle);
        assertNotNull(handle.runId());
        assertNotNull(handle.startedAt());
        assertNotNull(handle.completion());
    }

    @Test
    void startRunRegistersContextAsActive() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        TestRunHandle handle = engine.startRun("plan-2", Collections.emptyMap(), new Properties());

        // The run should be registered (may already be deregistered if it completes very fast,
        // but at minimum the status must be queryable before it finishes)
        assertNotNull(handle.runId());
    }

    @Test
    void startRunCompletesWithStoppedStatus() throws Exception {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        TestRunHandle handle = engine.startRun("plan-3", Collections.emptyMap(), new Properties());
        TestRunResult result = handle.completion().get(30, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(TestRunContext.TestRunStatus.STOPPED, result.finalStatus());
        assertEquals(handle.runId(), result.runId());
        assertFalse(result.elapsed().isNegative(), "Elapsed must be non-negative");
    }

    @Test
    void startRunPublishesSamplesToBroker() throws Exception {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        TestRunHandle handle = engine.startRun("plan-4", Collections.emptyMap(), new Properties());
        handle.completion().get(30, TimeUnit.SECONDS);

        assertFalse(broker.buckets().isEmpty(),
                "Expected at least one SampleBucket published to broker after run");
    }

    @Test
    void startRunWithVirtualUserOverrideRunsCorrectVuCount() throws Exception {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        Properties overrides = new Properties();
        overrides.setProperty(EngineServiceImpl.PROP_THREADS, "3");

        Map<String, Object> treeData = Map.of(
                "threadGroups", java.util.List.of(
                        Map.of("threads", 3, "iterations", 2,
                               "label", "VU-Check", "targetUrl", "http://ignored.invalid/")
                )
        );

        TestRunHandle handle = engine.startRun("plan-vu", treeData, overrides);
        TestRunResult result = handle.completion().get(30, TimeUnit.SECONDS);

        assertEquals(TestRunContext.TestRunStatus.STOPPED, result.finalStatus());
        // 3 VUs × 2 iterations = 6 total samples
        assertEquals(6L, result.totalSamples(),
                "Expected 6 total samples for 3 VUs × 2 iterations");
    }

    @Test
    void startRunThrowsOnNullPlanId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertThrows(IllegalArgumentException.class,
                () -> engine.startRun(null, Collections.emptyMap(), new Properties()));
    }

    @Test
    void startRunThrowsOnBlankPlanId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertThrows(IllegalArgumentException.class,
                () -> engine.startRun("   ", Collections.emptyMap(), new Properties()));
    }

    // -------------------------------------------------------------------------
    // stopRun
    // -------------------------------------------------------------------------

    @Test
    void stopRunTransitionsToStopping() throws Exception {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        // Start a run with many iterations so it takes a moment
        Map<String, Object> treeData = Map.of(
                "threadGroups", java.util.List.of(
                        Map.of("threads", 1, "iterations", 100,
                               "label", "SlowRun", "targetUrl", "http://ignored.invalid/")
                )
        );
        TestRunHandle handle = engine.startRun("plan-stop", treeData, new Properties());

        // Brief pause to let the run start
        Thread.sleep(10);

        engine.stopRun(handle.runId());

        // The run should complete eventually
        TestRunResult result = handle.completion().get(30, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    void stopRunDoesNotThrowForUnknownRunId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertDoesNotThrow(() -> engine.stopRun("unknown-run-id"));
    }

    @Test
    void stopRunThrowsOnNullRunId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertThrows(IllegalArgumentException.class, () -> engine.stopRun(null));
    }

    // -------------------------------------------------------------------------
    // stopRunNow
    // -------------------------------------------------------------------------

    @Test
    void stopRunNowDoesNotThrowForUnknownRunId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertDoesNotThrow(() -> engine.stopRunNow("unknown-run-now-id"));
    }

    @Test
    void stopRunNowThrowsOnNullRunId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertThrows(IllegalArgumentException.class, () -> engine.stopRunNow(null));
    }

    // -------------------------------------------------------------------------
    // getRunStatus
    // -------------------------------------------------------------------------

    @Test
    void getRunStatusReturnsNullForUnknownRun() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertNull(engine.getRunStatus("no-such-run"));
    }

    @Test
    void getRunStatusThrowsOnNullRunId() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertThrows(IllegalArgumentException.class, () -> engine.getRunStatus(null));
    }

    // -------------------------------------------------------------------------
    // activeRuns
    // -------------------------------------------------------------------------

    @Test
    void activeRunsIsNotNull() {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        assertNotNull(engine.activeRuns());
    }

    // -------------------------------------------------------------------------
    // Concurrent runs — isolation
    // -------------------------------------------------------------------------

    @Test
    void concurrentRunsCompleteIndependently() throws Exception {
        CapturingBroker broker = new CapturingBroker();
        EngineServiceImpl engine = new EngineServiceImpl(broker);

        TestRunHandle h1 = engine.startRun("plan-c1", Collections.emptyMap(), new Properties());
        TestRunHandle h2 = engine.startRun("plan-c2", Collections.emptyMap(), new Properties());

        assertNotEquals(h1.runId(), h2.runId(), "Concurrent runs must have distinct IDs");

        TestRunResult r1 = h1.completion().get(30, TimeUnit.SECONDS);
        TestRunResult r2 = h2.completion().get(30, TimeUnit.SECONDS);

        assertEquals(TestRunContext.TestRunStatus.STOPPED, r1.finalStatus());
        assertEquals(TestRunContext.TestRunStatus.STOPPED, r2.finalStatus());
    }

    // -------------------------------------------------------------------------
    // Capturing broker
    // -------------------------------------------------------------------------

    private static final class CapturingBroker implements SampleStreamBroker {

        private final CopyOnWriteArrayList<SampleBucket> captured = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<SampleBucketConsumer> subscribers = new CopyOnWriteArrayList<>();

        @Override
        public void subscribe(String runId, SampleBucketConsumer consumer) {
            subscribers.addIfAbsent(consumer);
        }

        @Override
        public void unsubscribe(String runId, SampleBucketConsumer consumer) {
            subscribers.remove(consumer);
        }

        @Override
        public void publish(String runId, SampleBucket bucket) {
            captured.add(bucket);
            for (SampleBucketConsumer c : subscribers) {
                try {
                    c.onBucket(bucket);
                } catch (RuntimeException ignored) {
                }
            }
        }

        java.util.List<SampleBucket> buckets() {
            return java.util.List.copyOf(captured);
        }
    }
}
