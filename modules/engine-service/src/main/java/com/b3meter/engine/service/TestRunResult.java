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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable summary of a completed test run.
 *
 * <p>Carried by the {@link TestRunHandle#completion()} future when a run ends.
 * Consumers use this to evaluate SLA compliance, generate reports, and post
 * notifications.
 *
 * <p>Constraints:
 * <ul>
 *   <li>{@code totalSamples} must be &gt;= 0</li>
 *   <li>{@code errorCount} must be &gt;= 0 and &lt;= {@code totalSamples}</li>
 *   <li>{@code elapsed} must not be negative</li>
 * </ul>
 *
 * @param runId        unique identifier for the run; never {@code null}
 * @param finalStatus  the lifecycle status at which the run ended; never {@code null}
 * @param startedAt    timestamp when the run was submitted; never {@code null}
 * @param endedAt      timestamp when the run completed; never {@code null}
 * @param totalSamples total number of samples collected during the run; must be &gt;= 0
 * @param errorCount   number of samples that were marked as errors; must be &gt;= 0
 * @param elapsed      wall-clock duration from start to end; must not be negative
 */
public record TestRunResult(
        String runId,
        TestRunContext.TestRunStatus finalStatus,
        Instant startedAt,
        Instant endedAt,
        long totalSamples,
        long errorCount,
        Duration elapsed
) {

    /**
     * Compact canonical constructor — validates all fields.
     */
    public TestRunResult {
        Objects.requireNonNull(runId,       "runId must not be null");
        Objects.requireNonNull(finalStatus, "finalStatus must not be null");
        Objects.requireNonNull(startedAt,   "startedAt must not be null");
        Objects.requireNonNull(endedAt,     "endedAt must not be null");
        Objects.requireNonNull(elapsed,     "elapsed must not be null");
        if (totalSamples < 0) {
            throw new IllegalArgumentException("totalSamples must be >= 0, got: " + totalSamples);
        }
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must be >= 0, got: " + errorCount);
        }
        if (errorCount > totalSamples) {
            throw new IllegalArgumentException(
                    "errorCount (" + errorCount + ") must be <= totalSamples (" + totalSamples + ")");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative, got: " + elapsed);
        }
    }

    /**
     * Computes the error rate as a percentage (0.0–100.0).
     *
     * @return 0.0 if no samples were collected; otherwise {@code (errorCount / totalSamples) * 100}
     */
    public double errorPercent() {
        if (totalSamples == 0) {
            return 0.0;
        }
        return (double) errorCount / totalSamples * 100.0;
    }
}
