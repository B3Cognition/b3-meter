package com.jmeternext.web.api.controller.dto;

import java.time.Instant;

/**
 * API response representation of a test plan.
 *
 * <p>Maps one-to-one to the columns of the {@code test_plans} table that are
 * safe to expose over the public API (soft-delete timestamp is excluded).
 */
public record TestPlanDto(
        String id,
        String name,
        String ownerId,
        String treeData,
        Instant createdAt,
        Instant updatedAt
) {}
