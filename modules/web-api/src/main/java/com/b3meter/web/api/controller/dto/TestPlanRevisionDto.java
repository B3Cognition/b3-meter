package com.jmeternext.web.api.controller.dto;

import java.time.Instant;

/**
 * API response representation of a single revision in a test plan's history.
 *
 * <p>Revisions are append-only; they are never updated or deleted.
 */
public record TestPlanRevisionDto(
        String id,
        String planId,
        int revisionNumber,
        String treeData,
        Instant createdAt,
        String author
) {}
