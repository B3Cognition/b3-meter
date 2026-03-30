package com.jmeternext.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code test_plans} table.
 *
 * <p>A {@code null} {@code deletedAt} means the plan is active.
 * A non-null {@code deletedAt} means the plan has been soft-deleted.
 */
public record TestPlanEntity(
        String id,
        String name,
        String ownerId,
        String treeData,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}
