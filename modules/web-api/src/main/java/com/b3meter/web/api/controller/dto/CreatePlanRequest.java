package com.jmeternext.web.api.controller.dto;

/**
 * Request body for creating a new test plan.
 *
 * <p>{@code name} is required; {@code ownerId} is optional (defaults to "system" when absent).
 */
public record CreatePlanRequest(
        String name,
        String ownerId
) {}
