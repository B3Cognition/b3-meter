package com.jmeternext.engine.service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight handle returned to the caller when a test run is submitted.
 *
 * <p>The handle gives the caller:
 * <ul>
 *   <li>The {@code runId} — unique identifier assigned to this execution</li>
 *   <li>The {@code startedAt} — timestamp at which the run was submitted</li>
 *   <li>A {@link CompletableFuture} that resolves with a {@link TestRunResult} when the
 *       run completes (successfully or with an error)</li>
 * </ul>
 *
 * <p>The {@code completion} future is never completed exceptionally by the engine for
 * normal error conditions (sampler errors, plan misconfiguration). Instead, it resolves
 * with a {@link TestRunResult} whose {@code finalStatus} is
 * {@link TestRunContext.TestRunStatus#ERROR}. The future is completed exceptionally only
 * for unexpected engine-level failures (e.g., JVM errors).
 *
 * @param runId      unique identifier for this test run; never {@code null}
 * @param startedAt  timestamp when the run was submitted; never {@code null}
 * @param completion future that resolves when the run ends; never {@code null}
 */
public record TestRunHandle(
        String runId,
        Instant startedAt,
        CompletableFuture<TestRunResult> completion
) {

    /**
     * Compact canonical constructor — validates that no field is {@code null}.
     */
    public TestRunHandle {
        Objects.requireNonNull(runId,      "runId must not be null");
        Objects.requireNonNull(startedAt,  "startedAt must not be null");
        Objects.requireNonNull(completion, "completion must not be null");
    }
}
