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
package com.b3meter.web.api.bridge;

import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.UIBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebUIBridge}.
 *
 * <p>Uses a {@link FakeEventSink} — an in-memory recording implementation of
 * {@link EventSink} — rather than a live HTTP connection, so tests run without
 * a Spring application context.
 */
class WebUIBridgeTest {

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /** In-memory sink that records every event sent to it. */
    static final class FakeEventSink implements EventSink {

        private final List<SentEvent> events = new ArrayList<>();
        private boolean completed = false;
        private boolean failNextSend = false;

        @Override
        public void send(String eventName, Map<String, Object> payload) {
            if (failNextSend) {
                failNextSend = false;
                throw new UncheckedIOException("simulated client disconnect",
                        new java.io.IOException("broken pipe"));
            }
            // Use HashMap copy to allow null values (Map.copyOf rejects nulls).
            events.add(new SentEvent(eventName, new HashMap<>(payload)));
        }

        @Override
        public void complete() {
            completed = true;
        }

        List<SentEvent> getEvents() { return List.copyOf(events); }
        boolean isCompleted() { return completed; }
        void failOnNextSend() { failNextSend = true; }
    }

    record SentEvent(String name, Map<String, Object> payload) {}

    /**
     * Subclass that allows controlling the epoch second returned, making
     * the time-bucket aggregation deterministic in tests.
     */
    static class ControlledTimeWebUIBridge extends WebUIBridge {
        private long fixedSecond;

        ControlledTimeWebUIBridge(EventSink sink, long initialSecond) {
            super(sink);
            this.fixedSecond = initialSecond;
        }

        void advanceSecond() {
            fixedSecond++;
        }

        @Override
        long epochSecond() {
            return fixedSecond;
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private FakeEventSink fakeSink;
    private WebUIBridge bridge;

    /** Minimal no-op UIBridge for constructing TestRunContext fixtures in tests. */
    private static final UIBridge NO_OP_BRIDGE = new UIBridge() {
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

    private static final TestRunContext CTX = TestRunContext.builder()
            .runId("run-42")
            .planPath("/plans/smoke.jmx")
            .virtualUsers(10)
            .durationSeconds(60)
            .uiBridge(NO_OP_BRIDGE)
            .build();

    @BeforeEach
    void setUp() {
        fakeSink = new FakeEventSink();
        bridge = new WebUIBridge(fakeSink);
    }

    // ------------------------------------------------------------------
    // Structural
    // ------------------------------------------------------------------

    @Test
    void constructorRejectsNullSink() {
        assertThrows(IllegalArgumentException.class, () -> new WebUIBridge(null));
    }

    @Test
    void implementsUIBridgeInterface() {
        assertTrue(bridge instanceof UIBridge);
    }

    // ------------------------------------------------------------------
    // onTestStarted
    // ------------------------------------------------------------------

    @Test
    void onTestStartedEmitsTestStartedEvent() {
        bridge.onTestStarted(CTX);

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_TEST_STARTED, event.name());
        assertEquals("run-42", event.payload().get("runId"));
        assertEquals("/plans/smoke.jmx", event.payload().get("planPath"));
    }

    @Test
    void onTestStartedIncludesVirtualUsersAndDuration() {
        bridge.onTestStarted(CTX);

        Map<String, Object> payload = fakeSink.getEvents().get(0).payload();
        assertEquals(10, payload.get("virtualUsers"));
        assertEquals(60L, payload.get("durationSeconds"));
    }

    // ------------------------------------------------------------------
    // onTestEnded
    // ------------------------------------------------------------------

    @Test
    void onTestEndedEmitsTestEndedEventAndCompletesSink() {
        bridge.onTestEnded(CTX);

        List<SentEvent> events = fakeSink.getEvents();
        // May have a bucket flush event before test.ended
        SentEvent lastEvent = events.get(events.size() - 1);
        assertEquals(WebUIBridge.EVENT_TEST_ENDED, lastEvent.name());
        assertTrue(fakeSink.isCompleted());
    }

    @Test
    void onTestEndedPayloadContainsRunId() {
        bridge.onTestEnded(CTX);

        List<SentEvent> events = fakeSink.getEvents();
        SentEvent testEnded = events.stream()
                .filter(e -> e.name().equals(WebUIBridge.EVENT_TEST_ENDED))
                .findFirst()
                .orElseThrow();
        assertEquals("run-42", testEnded.payload().get("runId"));
    }

    // ------------------------------------------------------------------
    // onSample (original method)
    // ------------------------------------------------------------------

    @Test
    void onSampleEmitsSampleBucketEvent() {
        bridge.onSample(CTX, 42.5, 1.2);

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_SAMPLE_BUCKET, event.name());
        assertEquals(42.5, event.payload().get("samplesPerSecond"));
        assertEquals(1.2, event.payload().get("errorPercent"));
    }

    // ------------------------------------------------------------------
    // onThreadStarted / onThreadFinished (audit findings 1 & 2)
    // ------------------------------------------------------------------

    @Test
    void onThreadStartedEmitsThreadStartedEvent() {
        bridge.onThreadStarted(CTX, "Thread Group 1-1", 1);

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_THREAD_STARTED, event.name());
        assertEquals("run-42", event.payload().get("runId"));
        assertEquals("Thread Group 1-1", event.payload().get("threadName"));
        assertEquals(1, event.payload().get("activeThreadCount"));
    }

    @Test
    void onThreadFinishedEmitsThreadFinishedEvent() {
        bridge.onThreadFinished(CTX, "Thread Group 1-1", 0);

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_THREAD_FINISHED, event.name());
        assertEquals(0, event.payload().get("activeThreadCount"));
    }

    @Test
    void onThreadStartedAndFinishedEmitCorrectCounts() {
        bridge.onThreadStarted(CTX, "TG-1", 5);
        bridge.onThreadFinished(CTX, "TG-1", 4);

        List<SentEvent> events = fakeSink.getEvents();
        assertEquals(2, events.size());
        assertEquals(5, events.get(0).payload().get("activeThreadCount"));
        assertEquals(4, events.get(1).payload().get("activeThreadCount"));
    }

    // ------------------------------------------------------------------
    // reportError (audit finding 3)
    // ------------------------------------------------------------------

    @Test
    void reportErrorEmitsErrorEvent() {
        bridge.reportError("Connection refused", "Network Error");

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_ERROR, event.name());
        assertEquals("Connection refused", event.payload().get("message"));
        assertEquals("Network Error", event.payload().get("title"));
    }

    @Test
    void reportErrorWithNullMessageDoesNotThrow() {
        assertDoesNotThrow(() -> bridge.reportError(null, "Error"));
    }

    // ------------------------------------------------------------------
    // reportInfo (audit finding 3)
    // ------------------------------------------------------------------

    @Test
    void reportInfoEmitsInfoEvent() {
        bridge.reportInfo("Plan loaded successfully", "Info");

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent event = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_INFO, event.name());
        assertEquals("Plan loaded successfully", event.payload().get("message"));
    }

    // ------------------------------------------------------------------
    // refreshUI (audit finding 4) — no-op
    // ------------------------------------------------------------------

    @Test
    void refreshUIEmitsNoEvents() {
        bridge.refreshUI();

        assertTrue(fakeSink.getEvents().isEmpty());
    }

    // ------------------------------------------------------------------
    // promptPassword (audit finding 5) — always throws
    // ------------------------------------------------------------------

    @Test
    void promptPasswordAlwaysThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> bridge.promptPassword("Enter keystore password:"));
    }

    @Test
    void promptPasswordThrowsEvenWithNullMessage() {
        assertThrows(UnsupportedOperationException.class,
                () -> bridge.promptPassword(null));
    }

    // ------------------------------------------------------------------
    // onSampleReceived — 1-second bucket aggregation (audit findings 6 & 7)
    // ------------------------------------------------------------------

    @Test
    void onSampleReceivedInSameSecondDoesNotEmitImmediately() {
        ControlledTimeWebUIBridge controlled = new ControlledTimeWebUIBridge(fakeSink, 1000L);

        controlled.onSampleReceived(CTX, "HTTP Request", 100L, true);
        controlled.onSampleReceived(CTX, "HTTP Request", 200L, true);

        // No bucket emitted yet — still in the same second
        assertTrue(fakeSink.getEvents().isEmpty());
    }

    @Test
    void onSampleReceivedAcrossTwoSecondsEmitsFirstBucket() {
        ControlledTimeWebUIBridge controlled = new ControlledTimeWebUIBridge(fakeSink, 1000L);

        controlled.onSampleReceived(CTX, "HTTP Request", 100L, true);
        controlled.onSampleReceived(CTX, "HTTP Request", 200L, false);
        controlled.advanceSecond();
        // This sample is in the next second — triggers flush of previous bucket
        controlled.onSampleReceived(CTX, "HTTP Request", 150L, true);

        assertEquals(1, fakeSink.getEvents().size());
        SentEvent bucket = fakeSink.getEvents().get(0);
        assertEquals(WebUIBridge.EVENT_SAMPLE_BUCKET, bucket.name());
        assertEquals(2L, bucket.payload().get("sampleCount"));
        assertEquals(1L, bucket.payload().get("errorCount"));
        assertEquals(150L, bucket.payload().get("avgElapsedMs")); // (100+200)/2 = 150
        assertEquals(1000L, bucket.payload().get("bucketSecond"));
    }

    @Test
    void onSampleReceivedAllSuccessHasZeroErrorCount() {
        ControlledTimeWebUIBridge controlled = new ControlledTimeWebUIBridge(fakeSink, 2000L);

        controlled.onSampleReceived(CTX, "GET /health", 50L, true);
        controlled.onSampleReceived(CTX, "GET /health", 60L, true);
        controlled.advanceSecond();
        controlled.onSampleReceived(CTX, "GET /health", 55L, true); // triggers flush

        SentEvent bucket = fakeSink.getEvents().get(0);
        assertEquals(0L, bucket.payload().get("errorCount"));
        assertEquals(2L, bucket.payload().get("sampleCount"));
    }

    @Test
    void onSampleReceivedEmptyBucketNotFlushedOnTestEnded() {
        // No samples received — onTestEnded should not emit a spurious bucket event
        bridge.onTestEnded(CTX);

        List<SentEvent> events = fakeSink.getEvents();
        long bucketEvents = events.stream()
                .filter(e -> e.name().equals(WebUIBridge.EVENT_SAMPLE_BUCKET))
                .count();
        assertEquals(0, bucketEvents);
    }

    // ------------------------------------------------------------------
    // Resilience: disconnected client
    // ------------------------------------------------------------------

    @Test
    void onThreadStartedDoesNotThrowWhenSinkFails() {
        fakeSink.failOnNextSend();
        // Engine thread must not crash when client disconnects
        assertDoesNotThrow(() -> bridge.onThreadStarted(CTX, "TG-1", 1));
    }

    @Test
    void reportErrorDoesNotThrowWhenSinkFails() {
        fakeSink.failOnNextSend();
        assertDoesNotThrow(() -> bridge.reportError("something", "Error"));
    }
}
