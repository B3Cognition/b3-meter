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
package com.b3meter.engine.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SampleBucketConsumer}.
 *
 * <p>The interface is {@code @FunctionalInterface} — tests verify that:
 * <ul>
 *   <li>A lambda can be used as a consumer (functional interface contract)</li>
 *   <li>{@code onBucket} is invoked with the correct bucket</li>
 *   <li>Multiple invocations deliver all buckets in order</li>
 * </ul>
 */
class SampleBucketConsumerTest {

    private static final Instant TS = Instant.parse("2026-03-25T10:00:00Z");

    @Test
    void lambdaImplementationReceivesBucket() {
        List<SampleBucket> received = new ArrayList<>();
        SampleBucketConsumer consumer = received::add;

        SampleBucket bucket = validBucket("HTTP GET");
        consumer.onBucket(bucket);

        assertEquals(1, received.size());
        assertSame(bucket, received.get(0));
    }

    @Test
    void multipleInvocationsDeliverAllBuckets() {
        List<SampleBucket> received = new ArrayList<>();
        SampleBucketConsumer consumer = received::add;

        SampleBucket b1 = validBucket("HTTP GET");
        SampleBucket b2 = validBucket("HTTP POST");
        consumer.onBucket(b1);
        consumer.onBucket(b2);

        assertEquals(2, received.size());
        assertSame(b1, received.get(0));
        assertSame(b2, received.get(1));
    }

    @Test
    void consumerCanBeUsedAsMethodReference() {
        List<SampleBucket> sink = new ArrayList<>();
        // method reference as functional interface
        SampleBucketConsumer consumer = sink::add;
        SampleBucket bucket = validBucket("DB Query");

        consumer.onBucket(bucket);

        assertFalse(sink.isEmpty());
        assertSame(bucket, sink.get(0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SampleBucket validBucket(String label) {
        return new SampleBucket(TS, label, 10L, 0L, 100.0, 50.0, 200.0, 150.0, 180.0, 195.0, 10.0);
    }
}
