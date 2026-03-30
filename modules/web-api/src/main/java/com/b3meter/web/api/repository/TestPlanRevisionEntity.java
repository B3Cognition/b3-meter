package com.jmeternext.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code test_plan_revisions} table.
 *
 * <p>Revisions are append-only; they are never updated or deleted.
 * The {@code revisionNumber} starts at 1 and increments per plan.
 */
public record TestPlanRevisionEntity(
        String id,
        String planId,
        int revisionNumber,
        String treeData,
        Instant createdAt,
        String author
) {}
