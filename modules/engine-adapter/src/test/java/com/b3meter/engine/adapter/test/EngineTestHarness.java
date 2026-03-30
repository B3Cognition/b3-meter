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
package com.b3meter.engine.adapter.test;

import com.b3meter.engine.adapter.InMemorySampleStreamBroker;
import com.b3meter.engine.adapter.NoOpUIBridge;
import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.SampleBucketConsumer;
import com.b3meter.engine.service.TestRunContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared test harness for engine-adapter and engine-service unit and integration tests.
 *
 * <p>Provides a pre-wired {@link TestRunContext} and {@link InMemorySampleStreamBroker}
 * configured with safe defaults for use in tests. Callers can:
 * <ul>
 *   <li>Access the context and broker directly via {@link #context()} and {@link #broker()}</li>
 *   <li>Publish synthetic {@link SampleBucket} instances via {@link #publishSamples}</li>
 *   <li>Collect all published buckets via {@link #collectBuckets(Duration)}</li>
 * </ul>
 *
 * <p>Thread-safety: this class is not thread-safe itself. Concurrent test execution
 * should use separate {@code EngineTestHarness} instances.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   EngineTestHarness harness = new EngineTestHarness();
 *   harness.publishSamples("HTTP GET", 5, 120);
 *   List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
 *   assertEquals(5, buckets.size());
 * }</pre>
 */
public final class EngineTestHarness {

    private final TestRunContext context;
    private final InMemorySampleStreamBroker broker;

    /** Buckets collected by the harness's internal subscriber. */
    private final List<SampleBucket> collectedBuckets = new CopyOnWriteArrayList<>();

    /** The harness consumer registered on the broker to capture published buckets. */
    private final SampleBucketConsumer harnessConsumer;

    /**
     * Creates a new harness with a randomly-assigned runId, a single virtual user,
     * 30-second duration, and a {@link NoOpUIBridge}.
     */
    public EngineTestHarness() {
        broker  = new InMemorySampleStreamBroker();
        context = TestRunContext.builder()
                .runId(UUID.randomUUID().toString())
                .planPath("test-plan.jmx")
                .virtualUsers(1)
                .durationSeconds(30)
                .uiBridge(NoOpUIBridge.INSTANCE)
                .build();

        // Wire the internal consumer so publishSamples() → collectBuckets() round-trip works
        harnessConsumer = collectedBuckets::add;
        broker.subscribe(context.getRunId(), harnessConsumer);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the pre-built {@link TestRunContext} for this harness instance.
     *
     * @return the run context; never {@code null}
     */
    public TestRunContext context() {
        return context;
    }

    /**
     * Returns the {@link InMemorySampleStreamBroker} wired to this harness.
     *
     * @return the broker; never {@code null}
     */
    public InMemorySampleStreamBroker broker() {
        return broker;
    }

    // -------------------------------------------------------------------------
    // Sample publishing helpers
    // -------------------------------------------------------------------------

    /**
     * Publishes {@code count} synthetic {@link SampleBucket} instances for {@code label}
     * with a uniform average response time of {@code avgMs} milliseconds.
     *
     * <p>Each bucket is published to the broker for this harness's runId with a
     * sequentially-incrementing timestamp (1-second steps starting at epoch second 0).
     * Error count is always 0; throughput equals 1.0 (one sample per bucket-second).
     *
     * @param label  sampler label; must not be {@code null}
     * @param count  number of buckets to publish; must be &gt;= 1
     * @param avgMs  average response time in milliseconds; must be &gt;= 0
     * @throws NullPointerException     if {@code label} is {@code null}
     * @throws IllegalArgumentException if {@code count} &lt; 1 or {@code avgMs} &lt; 0
     */
    public void publishSamples(String label, int count, long avgMs) {
        Objects.requireNonNull(label, "label must not be null");
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, got: " + count);
        }
        if (avgMs < 0) {
            throw new IllegalArgumentException("avgMs must be >= 0, got: " + avgMs);
        }

        for (int i = 0; i < count; i++) {
            SampleBucket bucket = new SampleBucket(
                    Instant.ofEpochSecond(i),
                    label,
                    /* sampleCount     */ 1L,
                    /* errorCount      */ 0L,
                    /* avgResponseTime */ (double) avgMs,
                    /* minResponseTime */ (double) avgMs,
                    /* maxResponseTime */ (double) avgMs,
                    /* percentile90    */ (double) avgMs,
                    /* percentile95    */ (double) avgMs,
                    /* percentile99    */ (double) avgMs,
                    /* samplesPerSec   */ 1.0
            );
            broker.publish(context.getRunId(), bucket);
        }
    }

    // -------------------------------------------------------------------------
    // Bucket collection
    // -------------------------------------------------------------------------

    /**
     * Returns all {@link SampleBucket} instances collected by the harness's internal
     * subscriber since construction (or the last call to {@link #clearCollectedBuckets}).
     *
     * <p>Because {@link InMemorySampleStreamBroker} delivers synchronously,
     * all buckets published via {@link #publishSamples} are available immediately —
     * the {@code timeout} parameter is accepted for API symmetry but is not used for
     * blocking.
     *
     * @param timeout maximum time to wait for buckets; must not be {@code null}
     *                (reserved for future async implementations)
     * @return an unmodifiable snapshot of all buckets received so far; never {@code null}
     * @throws NullPointerException if {@code timeout} is {@code null}
     */
    public List<SampleBucket> collectBuckets(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        return Collections.unmodifiableList(new ArrayList<>(collectedBuckets));
    }

    /**
     * Clears all previously collected buckets, resetting the collection state.
     *
     * <p>Useful when a single harness instance is reused across multiple
     * {@link #publishSamples} invocations within the same test.
     */
    public void clearCollectedBuckets() {
        collectedBuckets.clear();
    }
}
