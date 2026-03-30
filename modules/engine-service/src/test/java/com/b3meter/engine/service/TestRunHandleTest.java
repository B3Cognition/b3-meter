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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TestRunHandle}.
 */
class TestRunHandleTest {

    // -------------------------------------------------------------------------
    // Construction — happy path
    // -------------------------------------------------------------------------

    @Test
    void constructsWithValidFields() {
        Instant now = Instant.now();
        CompletableFuture<TestRunResult> future = new CompletableFuture<>();

        TestRunHandle handle = new TestRunHandle("run-1", now, future);

        assertEquals("run-1", handle.runId());
        assertEquals(now, handle.startedAt());
        assertSame(future, handle.completion());
    }

    // -------------------------------------------------------------------------
    // Construction — null guards
    // -------------------------------------------------------------------------

    @Test
    void throwsOnNullRunId() {
        assertThrows(NullPointerException.class, () ->
                new TestRunHandle(null, Instant.now(), new CompletableFuture<>()));
    }

    @Test
    void throwsOnNullStartedAt() {
        assertThrows(NullPointerException.class, () ->
                new TestRunHandle("run-1", null, new CompletableFuture<>()));
    }

    @Test
    void throwsOnNullCompletion() {
        assertThrows(NullPointerException.class, () ->
                new TestRunHandle("run-1", Instant.now(), null));
    }

    // -------------------------------------------------------------------------
    // Record equality and hashCode
    // -------------------------------------------------------------------------

    @Test
    void equalHandlesHaveSameHashCode() {
        Instant now = Instant.now();
        CompletableFuture<TestRunResult> future = new CompletableFuture<>();

        TestRunHandle h1 = new TestRunHandle("run-x", now, future);
        TestRunHandle h2 = new TestRunHandle("run-x", now, future);

        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    void handlesWithDifferentRunIdsAreNotEqual() {
        Instant now = Instant.now();
        CompletableFuture<TestRunResult> future = new CompletableFuture<>();

        TestRunHandle h1 = new TestRunHandle("run-1", now, future);
        TestRunHandle h2 = new TestRunHandle("run-2", now, future);

        assertNotEquals(h1, h2);
    }

    // -------------------------------------------------------------------------
    // Completion future is live — can be resolved
    // -------------------------------------------------------------------------

    @Test
    void completionFutureCanBeResolvedWithResult() {
        CompletableFuture<TestRunResult> future = new CompletableFuture<>();
        TestRunHandle handle = new TestRunHandle("run-1", Instant.now(), future);

        Instant start = Instant.now();
        Instant end   = start.plusSeconds(5);
        TestRunResult result = new TestRunResult(
                "run-1", TestRunContext.TestRunStatus.STOPPED,
                start, end, 100L, 2L,
                java.time.Duration.ofSeconds(5));

        handle.completion().complete(result);

        assertTrue(handle.completion().isDone());
        assertSame(result, handle.completion().join());
    }
}
