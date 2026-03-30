package com.jmeternext.web.api.controller.dto;

/**
 * Request body for updating an existing test plan.
 *
 * <p>{@code name} is optional — when null the existing name is preserved.
 * {@code treeData} is optional — when null the existing tree_data is preserved.
 * {@code author} identifies who is making the change (stored in the revision record).
 */
public record UpdatePlanRequest(
        String name,
        String treeData,
        String author
) {}
