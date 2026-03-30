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
package com.b3meter.web.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private RateLimitFilter.LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RateLimitFilter.LoginAttemptTracker();
    }

    @Test
    void initiallyNotLocked() {
        assertFalse(tracker.isLocked());
    }

    @Test
    void nineFailuresDoNotLock() {
        for (int i = 0; i < 9; i++) tracker.recordFailure();
        assertFalse(tracker.isLocked());
        assertEquals(9, tracker.getFailureCount());
    }

    @Test
    void tenFailuresLock() {
        for (int i = 0; i < 10; i++) tracker.recordFailure();
        assertTrue(tracker.isLocked());
    }

    @Test
    void lockoutRemainingIsPositive() {
        for (int i = 0; i < 10; i++) tracker.recordFailure();
        assertTrue(tracker.lockoutRemainingSeconds() > 0);
    }

    @Test
    void resetClearsLock() {
        for (int i = 0; i < 10; i++) tracker.recordFailure();
        assertTrue(tracker.isLocked());
        tracker.reset();
        assertFalse(tracker.isLocked());
        assertEquals(0, tracker.getFailureCount());
    }

    @Test
    void successfulLoginResets() {
        for (int i = 0; i < 5; i++) tracker.recordFailure();
        assertEquals(5, tracker.getFailureCount());
        tracker.reset(); // simulates successful login
        assertEquals(0, tracker.getFailureCount());
    }

    @Test
    void filterInstanceTracksMultipleIPs() {
        RateLimitFilter filter = new RateLimitFilter();
        var attempts = filter.getAttempts();
        attempts.computeIfAbsent("1.2.3.4", k -> new RateLimitFilter.LoginAttemptTracker());
        attempts.computeIfAbsent("5.6.7.8", k -> new RateLimitFilter.LoginAttemptTracker());
        assertEquals(2, attempts.size());
    }
}
