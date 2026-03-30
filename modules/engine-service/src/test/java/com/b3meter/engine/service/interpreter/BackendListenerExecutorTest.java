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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BackendListenerExecutor}.
 */
class BackendListenerExecutorTest {

    @AfterEach
    void tearDown() {
        BackendListenerExecutor.reset();
    }

    @Test
    void metricsAccumulator_tracksCountAndErrors() {
        BackendListenerExecutor.MetricsAccumulator acc =
                new BackendListenerExecutor.MetricsAccumulator();

        SampleResult ok = new SampleResult("test");
        ok.setTotalTimeMs(100);
        ok.setSuccess(true);

        SampleResult fail = new SampleResult("test");
        fail.setTotalTimeMs(200);
        fail.setFailureMessage("error");

        acc.add(ok);
        acc.add(fail);

        BackendListenerExecutor.MetricsSnapshot snapshot = acc.snapshot();
        assertEquals(2, snapshot.count());
        assertEquals(1, snapshot.okCount());
        assertEquals(1, snapshot.koCount());
        assertEquals(150.0, snapshot.avgMs(), 0.01);
        assertEquals(100, snapshot.minMs());
        assertEquals(200, snapshot.maxMs());
    }

    @Test
    void metricsAccumulator_snapshotResetsCounters() {
        BackendListenerExecutor.MetricsAccumulator acc =
                new BackendListenerExecutor.MetricsAccumulator();

        SampleResult r = new SampleResult("test");
        r.setTotalTimeMs(50);
        acc.add(r);

        BackendListenerExecutor.MetricsSnapshot first = acc.snapshot();
        assertEquals(1, first.count());

        // After snapshot, counters should be reset
        BackendListenerExecutor.MetricsSnapshot second = acc.snapshot();
        assertEquals(0, second.count());
    }

    @Test
    void buildGraphitePayload_containsAllMetrics() {
        BackendListenerExecutor.MetricsSnapshot snapshot =
                new BackendListenerExecutor.MetricsSnapshot(10, 8, 2, 150.0, 50, 300);

        String payload = BackendListenerExecutor.buildGraphitePayload("jmeter", snapshot);

        assertTrue(payload.contains("jmeter.count 10"));
        assertTrue(payload.contains("jmeter.ok.count 8"));
        assertTrue(payload.contains("jmeter.ko.count 2"));
        assertTrue(payload.contains("jmeter.avg 150.0"));
        assertTrue(payload.contains("jmeter.min 50"));
        assertTrue(payload.contains("jmeter.max 300"));
    }

    @Test
    void buildInfluxLine_containsLineProtocol() {
        BackendListenerExecutor.MetricsSnapshot snapshot =
                new BackendListenerExecutor.MetricsSnapshot(5, 4, 1, 200.0, 100, 400);

        String line = BackendListenerExecutor.buildInfluxLine("jmeter", snapshot);

        assertTrue(line.startsWith("jmeter,type=aggregate"));
        assertTrue(line.contains("count=5i"));
        assertTrue(line.contains("ok=4i"));
        assertTrue(line.contains("ko=1i"));
        assertTrue(line.contains("min=100i"));
        assertTrue(line.contains("max=400i"));
    }

    @Test
    void recordSample_storesInAccumulator() {
        PlanNode node = PlanNode.builder("BackendListener", "Backend")
                .property("className", "graphite")
                .property("graphiteHost", "localhost")
                .property("graphitePort", 2003)
                .build();

        SampleResult r = new SampleResult("test-sampler");
        r.setTotalTimeMs(123);

        BackendListenerExecutor.recordSample(node, r);
        // No assertion on flush since Graphite is not available;
        // the test verifies recordSample does not throw
    }

    @Test
    void recordSample_nullNode_throws() {
        SampleResult r = new SampleResult("test");
        assertThrows(NullPointerException.class,
                () -> BackendListenerExecutor.recordSample(null, r));
    }

    @Test
    void recordSample_nullResult_throws() {
        PlanNode node = PlanNode.builder("BackendListener", "Backend").build();
        assertThrows(NullPointerException.class,
                () -> BackendListenerExecutor.recordSample(node, null));
    }

    @Test
    void stop_withoutStart_doesNotThrow() {
        PlanNode node = PlanNode.builder("BackendListener", "Backend").build();
        assertDoesNotThrow(() -> BackendListenerExecutor.stop(node));
    }
}
