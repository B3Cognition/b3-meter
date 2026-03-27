package com.jmeternext.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code test_runs} table.
 *
 * <p>Status lifecycle: {@code CREATED → RUNNING → COMPLETED | FAILED | STOPPED}.
 * {@code startedAt} and {@code endedAt} are null until the run transitions to the
 * corresponding states.
 */
public record TestRunEntity(
        String id,
        String planId,
        String status,
        Instant startedAt,
        Instant endedAt,
        long totalSamples,
        long errorCount,
        String ownerId
) {}
