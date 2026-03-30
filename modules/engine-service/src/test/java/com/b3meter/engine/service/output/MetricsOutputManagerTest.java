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
package com.b3meter.engine.service.output;

import com.b3meter.engine.service.SampleBucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MetricsOutputManager}.
 */
class MetricsOutputManagerTest {

    // =========================================================================
    // Test double
    // =========================================================================

    /**
     * Simple recording output that captures all samples it receives.
     */
    static class RecordingOutput implements MetricsOutput {
        final String outputName;
        final List<SampleBucket> received = new CopyOnWriteArrayList<>();
        volatile boolean started = false;
        volatile boolean stopped = false;

        RecordingOutput(String name) {
            this.outputName = name;
        }

        @Override public String name() { return outputName; }

        @Override
        public void start(String runId, Map<String, String> config) {
            started = true;
        }

        @Override
        public void writeSamples(List<SampleBucket> samples) {
            received.addAll(samples);
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    /**
     * Output that throws on writeSamples to test error isolation.
     */
    static class FailingOutput implements MetricsOutput {
        volatile boolean started = false;
        volatile boolean stopped = false;

        @Override public String name() { return "failing"; }

        @Override
        public void start(String runId, Map<String, String> config) {
            started = true;
        }

        @Override
        public void writeSamples(List<SampleBucket> samples) {
            throw new RuntimeException("Intentional failure");
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static SampleBucket makeBucket(String label) {
        return new SampleBucket(
                Instant.now(), label, 10, 1,
                50.0, 10.0, 200.0,
                90.0, 95.0, 99.0, 10.0);
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    void addOutputRejectsNull() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        assertThrows(NullPointerException.class, () -> mgr.addOutput(null));
    }

    @Test
    void getOutputsReturnsRegisteredOutputs() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput a = new RecordingOutput("a");
        RecordingOutput b = new RecordingOutput("b");
        mgr.addOutput(a);
        mgr.addOutput(b);

        List<MetricsOutput> outputs = mgr.getOutputs();
        assertEquals(2, outputs.size());
    }

    @Test
    @Timeout(10)
    void multipleOutputsReceiveSamples() throws InterruptedException {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out1 = new RecordingOutput("out1");
        RecordingOutput out2 = new RecordingOutput("out2");
        mgr.addOutput(out1);
        mgr.addOutput(out2);

        mgr.start("run-1", Map.of());

        mgr.onBucket(makeBucket("http-get"));
        mgr.onBucket(makeBucket("http-post"));

        // Wait for the 1-second flush cycle to pick up the samples
        Thread.sleep(2500);

        mgr.stop();

        assertTrue(out1.received.size() >= 2, "out1 should have received at least 2 samples");
        assertTrue(out2.received.size() >= 2, "out2 should have received at least 2 samples");
    }

    @Test
    @Timeout(10)
    void errorIsolationOneFailing() throws InterruptedException {
        MetricsOutputManager mgr = new MetricsOutputManager();
        FailingOutput failing = new FailingOutput();
        RecordingOutput healthy = new RecordingOutput("healthy");

        mgr.addOutput(failing);
        mgr.addOutput(healthy);

        mgr.start("run-2", Map.of());

        mgr.onBucket(makeBucket("test-label"));

        // Wait for flush
        Thread.sleep(2500);

        mgr.stop();

        // The healthy output should still receive samples despite the failing one
        assertTrue(healthy.received.size() >= 1,
                "Healthy output must receive samples even when another output fails");
    }

    @Test
    @Timeout(10)
    void startCallsStartOnAllOutputs() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out1 = new RecordingOutput("out1");
        RecordingOutput out2 = new RecordingOutput("out2");
        mgr.addOutput(out1);
        mgr.addOutput(out2);

        mgr.start("run-3", Map.of("key", "value"));

        assertTrue(out1.started, "out1 should have been started");
        assertTrue(out2.started, "out2 should have been started");

        mgr.stop();
    }

    @Test
    @Timeout(10)
    void stopCallsStopOnAllOutputs() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out1 = new RecordingOutput("out1");
        RecordingOutput out2 = new RecordingOutput("out2");
        mgr.addOutput(out1);
        mgr.addOutput(out2);

        mgr.start("run-4", Map.of());
        mgr.stop();

        assertTrue(out1.stopped, "out1 should have been stopped");
        assertTrue(out2.stopped, "out2 should have been stopped");
    }

    @Test
    void stopBeforeStartIsNoOp() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out = new RecordingOutput("out");
        mgr.addOutput(out);

        // Should not throw
        mgr.stop();
        assertFalse(out.stopped, "stop should be a no-op when not started");
    }

    @Test
    @Timeout(10)
    void closeCallsStop() {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out = new RecordingOutput("out");
        mgr.addOutput(out);

        mgr.start("run-5", Map.of());
        mgr.close();

        assertTrue(out.stopped, "close() should delegate to stop()");
    }

    @Test
    @Timeout(10)
    void finalFlushOnStop() throws InterruptedException {
        MetricsOutputManager mgr = new MetricsOutputManager();
        RecordingOutput out = new RecordingOutput("out");
        mgr.addOutput(out);

        mgr.start("run-6", Map.of());

        // Add a bucket and immediately stop (before the 1-second flush cycle)
        mgr.onBucket(makeBucket("final-flush"));
        mgr.stop();

        // The final flush in stop() should have delivered the bucket
        assertTrue(out.received.size() >= 1,
                "Final flush on stop should deliver buffered samples");
    }
}
