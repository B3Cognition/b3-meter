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
import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.TestRunContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EngineTestHarness}.
 */
class EngineTestHarnessTest {

    private EngineTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new EngineTestHarness();
    }

    // -------------------------------------------------------------------------
    // Construction defaults
    // -------------------------------------------------------------------------

    @Test
    void contextIsNotNull() {
        assertNotNull(harness.context());
    }

    @Test
    void brokerIsNotNull() {
        assertNotNull(harness.broker());
    }

    @Test
    void contextHasNonBlankRunId() {
        assertNotNull(harness.context().getRunId());
        assertFalse(harness.context().getRunId().isBlank());
    }

    @Test
    void contextHasPlanPath() {
        assertEquals("test-plan.jmx", harness.context().getPlanPath());
    }

    @Test
    void contextHasOneVirtualUser() {
        assertEquals(1, harness.context().getVirtualUsers());
    }

    @Test
    void contextHas30SecondDuration() {
        assertEquals(30L, harness.context().getDurationSeconds());
    }

    @Test
    void contextTypeIsTestRunContext() {
        assertInstanceOf(TestRunContext.class, harness.context());
    }

    @Test
    void brokerTypeIsInMemory() {
        assertInstanceOf(InMemorySampleStreamBroker.class, harness.broker());
    }

    // -------------------------------------------------------------------------
    // publishSamples — basic delivery
    // -------------------------------------------------------------------------

    @Test
    void publishSamplesDeliversCorrectCount() {
        harness.publishSamples("HTTP GET", 5, 120L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertEquals(5, buckets.size());
    }

    @Test
    void publishSamplesUsesSamplerLabel() {
        harness.publishSamples("My Sampler", 3, 100L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        for (SampleBucket b : buckets) {
            assertEquals("My Sampler", b.samplerLabel());
        }
    }

    @Test
    void publishSamplesUsesAvgMs() {
        harness.publishSamples("HTTP POST", 2, 250L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        for (SampleBucket b : buckets) {
            assertEquals(250.0, b.avgResponseTime(), 1e-9);
        }
    }

    @Test
    void publishSamplesProducesZeroErrors() {
        harness.publishSamples("DB Query", 4, 80L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        for (SampleBucket b : buckets) {
            assertEquals(0L, b.errorCount());
            assertEquals(0.0, b.errorPercent(), 1e-9);
        }
    }

    @Test
    void publishSamplesProducesSequentialTimestamps() {
        harness.publishSamples("HTTP GET", 3, 100L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertEquals(3, buckets.size());
        assertEquals(0L, buckets.get(0).timestamp().getEpochSecond());
        assertEquals(1L, buckets.get(1).timestamp().getEpochSecond());
        assertEquals(2L, buckets.get(2).timestamp().getEpochSecond());
    }

    @Test
    void publishSamplesWithZeroAvgMs() {
        harness.publishSamples("Fast Query", 1, 0L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertEquals(1, buckets.size());
        assertEquals(0.0, buckets.get(0).avgResponseTime(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // publishSamples — multiple invocations accumulate
    // -------------------------------------------------------------------------

    @Test
    void multiplePublishCallsAccumulateBuckets() {
        harness.publishSamples("HTTP GET",  3, 100L);
        harness.publishSamples("HTTP POST", 2, 200L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertEquals(5, buckets.size());
    }

    // -------------------------------------------------------------------------
    // clearCollectedBuckets
    // -------------------------------------------------------------------------

    @Test
    void clearCollectedBucketsEmptiesTheList() {
        harness.publishSamples("HTTP GET", 3, 100L);
        harness.clearCollectedBuckets();
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertTrue(buckets.isEmpty());
    }

    @Test
    void clearThenPublishCollectsOnlyNewBuckets() {
        harness.publishSamples("HTTP GET", 3, 100L);
        harness.clearCollectedBuckets();
        harness.publishSamples("HTTP POST", 2, 200L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertEquals(2, buckets.size());
    }

    // -------------------------------------------------------------------------
    // collectBuckets returns unmodifiable view
    // -------------------------------------------------------------------------

    @Test
    void collectBucketsReturnsUnmodifiableList() {
        harness.publishSamples("HTTP GET", 1, 100L);
        List<SampleBucket> buckets = harness.collectBuckets(Duration.ofSeconds(1));
        assertThrows(UnsupportedOperationException.class, () -> buckets.add(null));
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void publishSamplesThrowsOnNullLabel() {
        assertThrows(NullPointerException.class,
                () -> harness.publishSamples(null, 1, 100L));
    }

    @Test
    void publishSamplesThrowsOnZeroCount() {
        assertThrows(IllegalArgumentException.class,
                () -> harness.publishSamples("HTTP GET", 0, 100L));
    }

    @Test
    void publishSamplesThrowsOnNegativeCount() {
        assertThrows(IllegalArgumentException.class,
                () -> harness.publishSamples("HTTP GET", -1, 100L));
    }

    @Test
    void publishSamplesThrowsOnNegativeAvgMs() {
        assertThrows(IllegalArgumentException.class,
                () -> harness.publishSamples("HTTP GET", 1, -1L));
    }

    @Test
    void collectBucketsThrowsOnNullTimeout() {
        assertThrows(NullPointerException.class,
                () -> harness.collectBuckets(null));
    }

    // -------------------------------------------------------------------------
    // Isolation: two harness instances do not interfere
    // -------------------------------------------------------------------------

    @Test
    void twoHarnessInstancesAreIsolated() {
        EngineTestHarness harness2 = new EngineTestHarness();

        harness.publishSamples("HTTP GET", 3, 100L);
        harness2.publishSamples("HTTP GET", 7, 200L);

        assertEquals(3, harness.collectBuckets(Duration.ofSeconds(1)).size());
        assertEquals(7, harness2.collectBuckets(Duration.ofSeconds(1)).size());

        // Different runIds
        assertNotEquals(
                harness.context().getRunId(),
                harness2.context().getRunId());
    }
}
