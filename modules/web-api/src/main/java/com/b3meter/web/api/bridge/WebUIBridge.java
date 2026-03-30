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

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web-backend implementation of {@link UIBridge}.
 *
 * <p>Translates engine callbacks into Server-Sent Events (SSE) for real-time
 * delivery to the browser client. One {@code WebUIBridge} is created per active
 * test run and discarded when the run ends.
 *
 * <p>Aggregation behaviour:
 * <ul>
 *   <li>{@link #onSampleReceived} accumulates samples in 1-second time buckets.
 *       Each bucket is emitted when the next sample arrives in a new second.</li>
 *   <li>{@link #onThreadStarted} / {@link #onThreadFinished} emit immediately
 *       so the UI can track concurrency in real time.</li>
 *   <li>{@link #reportError} logs at WARNING level and emits an {@code error} SSE event.</li>
 * </ul>
 *
 * <p>All methods are safe to call from any thread.
 */
public class WebUIBridge implements UIBridge {

    /** SSE event name constants. */
    static final String EVENT_TEST_STARTED   = "test.started";
    static final String EVENT_TEST_ENDED     = "test.ended";
    static final String EVENT_THREAD_STARTED = "thread.started";
    static final String EVENT_THREAD_FINISHED = "thread.finished";
    static final String EVENT_SAMPLE_BUCKET  = "sample.bucket";
    static final String EVENT_ERROR          = "error";
    static final String EVENT_INFO           = "info";

    private static final Logger LOG = Logger.getLogger(WebUIBridge.class.getName());

    private final EventSink sink;

    // ---- 1-second sample aggregation ----
    private final Object bucketLock = new Object();
    private volatile long currentBucketSecond = -1;
    private final AtomicLong bucketSampleCount = new AtomicLong(0);
    private final AtomicLong bucketErrorCount  = new AtomicLong(0);
    private final AtomicLong bucketElapsedSum  = new AtomicLong(0);
    private final AtomicReference<String> currentRunId = new AtomicReference<>(null);

    /**
     * Creates a new {@code WebUIBridge} that emits events to the supplied sink.
     *
     * @param sink the event sink for the client connection; must not be null
     */
    public WebUIBridge(EventSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Emits a {@code test.started} SSE event.
     */
    @Override
    public void onTestStarted(TestRunContext context) {
        currentRunId.set(context.getRunId());
        trySend(EVENT_TEST_STARTED, buildRunPayload(context));
    }

    /**
     * Emits a {@code test.ended} SSE event, then completes the SSE stream.
     */
    @Override
    public void onTestEnded(TestRunContext context) {
        flushBucket(context.getRunId(), epochSecond());
        trySend(EVENT_TEST_ENDED, buildRunPayload(context));
        sink.complete();
    }

    /**
     * Emits a {@code sample.bucket} SSE event with aggregate throughput metrics.
     *
     * <p>This method is retained for compatibility with the original interface
     * contract. Implementers may call either this or {@link #onSampleReceived}.
     */
    @Override
    public void onSample(TestRunContext context, double samplesPerSecond, double errorPercent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", context.getRunId());
        payload.put("samplesPerSecond", samplesPerSecond);
        payload.put("errorPercent", errorPercent);
        trySend(EVENT_SAMPLE_BUCKET, payload);
    }

    // -------------------------------------------------------------------------
    // Thread lifecycle (audit findings 1 & 2)
    // -------------------------------------------------------------------------

    /**
     * Emits a {@code thread.started} SSE event.
     */
    @Override
    public void onThreadStarted(TestRunContext context, String threadName, int activeThreadCount) {
        trySend(EVENT_THREAD_STARTED, buildThreadPayload(context, threadName, activeThreadCount));
    }

    /**
     * Emits a {@code thread.finished} SSE event.
     */
    @Override
    public void onThreadFinished(TestRunContext context, String threadName, int activeThreadCount) {
        trySend(EVENT_THREAD_FINISHED, buildThreadPayload(context, threadName, activeThreadCount));
    }

    // -------------------------------------------------------------------------
    // Error / info reporting (audit finding 3)
    // -------------------------------------------------------------------------

    /**
     * Logs the error at WARNING level and emits an {@code error} SSE event.
     */
    @Override
    public void reportError(String message, String title) {
        LOG.log(Level.WARNING, "[{0}] {1}", new Object[]{title, message});
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("message", message);
        trySend(EVENT_ERROR, payload);
    }

    /**
     * Emits an {@code info} SSE event. No log entry is written (informational only).
     */
    @Override
    public void reportInfo(String message, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("message", message);
        trySend(EVENT_INFO, payload);
    }

    // -------------------------------------------------------------------------
    // UI refresh (audit finding 4) — no-op for web context
    // -------------------------------------------------------------------------

    /**
     * No-op: the web UI refreshes reactively via SSE; no server-side push needed.
     */
    @Override
    public void refreshUI() {
        // intentionally no-op — web UI is event-driven
    }

    // -------------------------------------------------------------------------
    // Password prompt (audit finding 5)
    // -------------------------------------------------------------------------

    /**
     * Always throws {@link UnsupportedOperationException}.
     *
     * <p>The web UI handles authentication through its own auth flow.
     * Credential prompts must never block a server thread waiting for user input.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public String promptPassword(String message) {
        throw new UnsupportedOperationException(
                "WebUIBridge does not support interactive password prompts. "
                        + "Configure credentials via the web UI authentication flow before starting the test.");
    }

    // -------------------------------------------------------------------------
    // Sample aggregation (audit findings 6 & 7)
    // -------------------------------------------------------------------------

    /**
     * Aggregates the sample into the current 1-second bucket.
     *
     * <p>When the sample falls into a new second, the previous bucket is emitted
     * as a {@code sample.bucket} SSE event before starting the new bucket.
     *
     * @param context      immutable context for the current run
     * @param samplerLabel label of the sampler that produced this result
     * @param elapsed      elapsed time in milliseconds
     * @param success      {@code true} if the sampler reported success
     */
    @Override
    public void onSampleReceived(TestRunContext context, String samplerLabel, long elapsed, boolean success) {
        long second = epochSecond();
        synchronized (bucketLock) {
            if (currentBucketSecond < 0) {
                currentBucketSecond = second;
            }
            if (second != currentBucketSecond) {
                flushBucket(context.getRunId(), currentBucketSecond);
                currentBucketSecond = second;
                bucketSampleCount.set(0);
                bucketErrorCount.set(0);
                bucketElapsedSum.set(0);
            }
            bucketSampleCount.incrementAndGet();
            if (!success) {
                bucketErrorCount.incrementAndGet();
            }
            bucketElapsedSum.addAndGet(elapsed);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void flushBucket(String runId, long bucketSecond) {
        long samples = bucketSampleCount.get();
        if (samples == 0) {
            return;
        }
        long errors  = bucketErrorCount.get();
        long elapsed = bucketElapsedSum.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        payload.put("bucketSecond", bucketSecond);
        payload.put("sampleCount", samples);
        payload.put("errorCount", errors);
        payload.put("avgElapsedMs", samples > 0 ? elapsed / samples : 0);
        trySend(EVENT_SAMPLE_BUCKET, payload);
    }

    private void trySend(String eventName, Map<String, Object> payload) {
        try {
            sink.send(eventName, payload);
        } catch (UncheckedIOException e) {
            // Client disconnected — log and stop emitting; do not crash the engine thread.
            LOG.log(Level.FINE, "SSE client disconnected while sending ''{0}'': {1}",
                    new Object[]{eventName, e.getMessage()});
        }
    }

    private Map<String, Object> buildRunPayload(TestRunContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", context.getRunId());
        payload.put("planPath", context.getPlanPath());
        payload.put("virtualUsers", context.getVirtualUsers());
        payload.put("durationSeconds", context.getDurationSeconds());
        return payload;
    }

    private Map<String, Object> buildThreadPayload(TestRunContext context,
                                                    String threadName,
                                                    int activeThreadCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", context.getRunId());
        payload.put("threadName", threadName);
        payload.put("activeThreadCount", activeThreadCount);
        return payload;
    }

    /** Returns the current Unix epoch second. Extracted for testability. */
    long epochSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Exposes the sink for testing purposes. */
    EventSink getSink() {
        return sink;
    }
}
