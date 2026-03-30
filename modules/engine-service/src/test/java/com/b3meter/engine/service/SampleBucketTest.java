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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SampleBucket}.
 */
class SampleBucketTest {

    private static final Instant TS = Instant.parse("2026-03-25T10:00:00Z");

    // -------------------------------------------------------------------------
    // Construction — happy path
    // -------------------------------------------------------------------------

    @Test
    void constructsWithValidFields() {
        SampleBucket bucket = validBucket();

        assertEquals(TS,         bucket.timestamp());
        assertEquals("HTTP GET", bucket.samplerLabel());
        assertEquals(100L,       bucket.sampleCount());
        assertEquals(5L,         bucket.errorCount());
        assertEquals(250.0,      bucket.avgResponseTime(), 1e-9);
        assertEquals(50.0,       bucket.minResponseTime(), 1e-9);
        assertEquals(900.0,      bucket.maxResponseTime(), 1e-9);
        assertEquals(400.0,      bucket.percentile90(),    1e-9);
        assertEquals(600.0,      bucket.percentile95(),    1e-9);
        assertEquals(850.0,      bucket.percentile99(),    1e-9);
        assertEquals(100.0,      bucket.samplesPerSecond(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void throwsOnNullTimestamp() {
        assertThrows(NullPointerException.class, () ->
                new SampleBucket(null, "label", 10, 0, 100, 50, 200, 150, 180, 195, 10));
    }

    @Test
    void throwsOnNullSamplerLabel() {
        assertThrows(NullPointerException.class, () ->
                new SampleBucket(TS, null, 10, 0, 100, 50, 200, 150, 180, 195, 10));
    }

    // -------------------------------------------------------------------------
    // Constraint guards
    // -------------------------------------------------------------------------

    @Test
    void throwsOnNegativeSampleCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", -1, 0, 100, 50, 200, 150, 180, 195, 10));
    }

    @Test
    void throwsOnNegativeErrorCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, -1, 100, 50, 200, 150, 180, 195, 10));
    }

    @Test
    void throwsWhenErrorCountExceedsSampleCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 5, 6, 100, 50, 200, 150, 180, 195, 5));
    }

    @Test
    void throwsOnNegativeAvgResponseTime() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, -1, 50, 200, 150, 180, 195, 10));
    }

    @Test
    void throwsOnNegativeMinResponseTime() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, -1, 200, 150, 180, 195, 10));
    }

    @Test
    void throwsWhenMaxLessThanMin() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, 200, 100, 150, 180, 195, 10));
    }

    @Test
    void throwsOnNegativePercentile90() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, 50, 200, -1, 180, 195, 10));
    }

    @Test
    void throwsOnNegativePercentile95() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, 50, 200, 150, -1, 195, 10));
    }

    @Test
    void throwsOnNegativePercentile99() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, 50, 200, 150, 180, -1, 10));
    }

    @Test
    void throwsOnNegativeSamplesPerSecond() {
        assertThrows(IllegalArgumentException.class, () ->
                new SampleBucket(TS, "label", 10, 0, 100, 50, 200, 150, 180, 195, -1));
    }

    // -------------------------------------------------------------------------
    // Edge cases: min == max is valid (single sample)
    // -------------------------------------------------------------------------

    @Test
    void minEqualToMaxIsValid() {
        assertDoesNotThrow(() ->
                new SampleBucket(TS, "label", 1, 0, 100, 100, 100, 100, 100, 100, 1));
    }

    @Test
    void zeroCountZeroErrorsIsValid() {
        assertDoesNotThrow(() ->
                new SampleBucket(TS, "label", 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    // -------------------------------------------------------------------------
    // errorPercent
    // -------------------------------------------------------------------------

    @Test
    void errorPercentIsZeroWhenNoSamples() {
        SampleBucket bucket = new SampleBucket(TS, "label", 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0.0, bucket.errorPercent(), 1e-9);
    }

    @Test
    void errorPercentComputedCorrectly() {
        SampleBucket bucket = new SampleBucket(TS, "label", 200, 50, 100, 50, 200, 150, 180, 195, 200);
        assertEquals(25.0, bucket.errorPercent(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SampleBucket validBucket() {
        return new SampleBucket(TS, "HTTP GET", 100L, 5L, 250.0, 50.0, 900.0, 400.0, 600.0, 850.0, 100.0);
    }
}
