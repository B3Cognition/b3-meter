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

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.SampleStreamBroker;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.UIBridge;
import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only factory for creating stub collaborators used by interpreter tests.
 *
 * <p>All stubs are pure JDK implementations (no Mockito, no WireMock) so that
 * they can live in the {@code engine-service} test classpath which has no
 * framework dependencies.
 */
final class StubInterpreterFactory {

    private StubInterpreterFactory() {}

    /**
     * Creates a {@link NodeInterpreter} backed by a no-op HTTP client and a no-op broker.
     */
    static NodeInterpreter create() {
        return new NodeInterpreter(noOpHttpClient(), noOpBroker());
    }

    /**
     * Returns an {@link HttpClientFactory} that always returns HTTP 200 with an empty body
     * and zero timing.
     */
    static HttpClientFactory noOpHttpClient() {
        return new HttpClientFactory() {
            @Override
            public HttpResponse execute(HttpRequest request) throws IOException {
                return new HttpResponse(
                        200,
                        HttpRequest.PROTOCOL_HTTP_1_1,
                        Map.of(),
                        new byte[0],
                        0L, 1L, 1L,
                        false
                );
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }

    /**
     * Returns a {@link SampleStreamBroker} that collects published buckets in memory.
     * Useful for assertions in tests that need to inspect published data.
     */
    static CapturingBroker capturingBroker() {
        return new CapturingBroker();
    }

    /**
     * Returns a no-op {@link UIBridge} implementation for test contexts.
     * Defined here because NoOpUIBridge lives in engine-adapter, not engine-service.
     */
    static UIBridge noOpUiBridge() {
        return new UIBridge() {
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
    }

    /**
     * Returns a {@link SampleStreamBroker} that silently discards all published buckets.
     */
    static SampleStreamBroker noOpBroker() {
        return new SampleStreamBroker() {
            @Override
            public void subscribe(String runId, SampleBucketConsumer consumer) {}

            @Override
            public void unsubscribe(String runId, SampleBucketConsumer consumer) {}

            @Override
            public void publish(String runId, SampleBucket bucket) {}
        };
    }

    // -------------------------------------------------------------------------
    // CapturingBroker
    // -------------------------------------------------------------------------

    /**
     * A {@link SampleStreamBroker} that records all published buckets for test assertions.
     */
    static final class CapturingBroker implements SampleStreamBroker {

        private final CopyOnWriteArrayList<SampleBucket> buckets = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<SampleBucketConsumer>> subs =
                new ConcurrentHashMap<>();

        @Override
        public void subscribe(String runId, SampleBucketConsumer consumer) {
            subs.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(consumer);
        }

        @Override
        public void unsubscribe(String runId, SampleBucketConsumer consumer) {
            var list = subs.get(runId);
            if (list != null) list.remove(consumer);
        }

        @Override
        public void publish(String runId, SampleBucket bucket) {
            buckets.add(bucket);
            var list = subs.get(runId);
            if (list != null) {
                for (SampleBucketConsumer c : list) {
                    try { c.onBucket(bucket); } catch (Exception ignored) {}
                }
            }
        }

        /** Returns an unmodifiable snapshot of all published buckets. */
        java.util.List<SampleBucket> publishedBuckets() {
            return java.util.Collections.unmodifiableList(buckets);
        }

        /** Returns the total sample count across all published buckets. */
        long totalSamples() {
            return buckets.stream().mapToLong(SampleBucket::sampleCount).sum();
        }

        /** Returns the total error count across all published buckets. */
        long totalErrors() {
            return buckets.stream().mapToLong(SampleBucket::errorCount).sum();
        }
    }
}
