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

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all currently active {@link TestRunContext} instances.
 *
 * <p>Supports concurrent multi-run execution by providing a single, thread-safe
 * store for all in-flight runs. Replaces any static-field-per-run patterns that
 * would prevent concurrent execution.
 *
 * <p>Thread-safety: all public methods delegate to a {@link ConcurrentHashMap}
 * and are therefore safe for concurrent access from multiple threads.
 *
 * <p>Lifecycle: a context is {@link #register registered} when its run is
 * submitted, and {@link #remove removed} when the run completes or is cancelled.
 * Callers are responsible for removing contexts at run completion to avoid leaks.
 *
 * <p>{@link #clear()} is provided exclusively for test isolation and must not be
 * called in production code.
 */
public final class TestRunContextRegistry {

    private static final ConcurrentHashMap<String, TestRunContext> ACTIVE_RUNS =
            new ConcurrentHashMap<>();

    private TestRunContextRegistry() {
        // utility class — no instances
    }

    /**
     * Registers a new context for an in-flight run.
     *
     * @param context the context to register; must not be {@code null}
     * @return the registered context (same reference as the argument)
     * @throws IllegalStateException    if a context with the same runId is already registered
     * @throws NullPointerException     if {@code context} is {@code null}
     */
    public static TestRunContext register(TestRunContext context) {
        Objects.requireNonNull(context, "context must not be null");
        TestRunContext existing = ACTIVE_RUNS.putIfAbsent(context.getRunId(), context);
        if (existing != null) {
            throw new IllegalStateException(
                    "A run with id '" + context.getRunId() + "' is already registered");
        }
        return context;
    }

    /**
     * Returns the active context for the given run id, or {@code null} if not found.
     *
     * @param runId the run identifier to look up; must not be {@code null}
     * @throws NullPointerException if {@code runId} is {@code null}
     */
    public static TestRunContext get(String runId) {
        return ACTIVE_RUNS.get(Objects.requireNonNull(runId, "runId must not be null"));
    }

    /**
     * Removes and returns the context for the given run id.
     *
     * <p>Should be called when a run completes or is cancelled to avoid memory leaks.
     *
     * @param runId the run identifier to remove; must not be {@code null}
     * @return the removed context, or {@code null} if no context was registered for this id
     * @throws NullPointerException if {@code runId} is {@code null}
     */
    public static TestRunContext remove(String runId) {
        return ACTIVE_RUNS.remove(Objects.requireNonNull(runId, "runId must not be null"));
    }

    /**
     * Returns an unmodifiable snapshot of all currently active run contexts.
     *
     * <p>The returned collection reflects the registry state at the moment of the call.
     * Subsequent registrations or removals are not reflected.
     */
    public static Collection<TestRunContext> activeRuns() {
        return Collections.unmodifiableCollection(ACTIVE_RUNS.values());
    }

    /**
     * Returns the number of currently registered (active) run contexts.
     */
    public static int activeRunCount() {
        return ACTIVE_RUNS.size();
    }

    /**
     * Removes all registered contexts.
     *
     * <p><strong>For test isolation only.</strong> Must not be called in production code.
     * Calling this method while runs are active will cause those runs to become
     * unreachable via the registry.
     */
    public static void clear() {
        ACTIVE_RUNS.clear();
    }
}
