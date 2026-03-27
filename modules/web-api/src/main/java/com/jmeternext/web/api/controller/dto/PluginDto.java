package com.jmeternext.web.api.controller.dto;

import java.time.Instant;

/**
 * API response representation of an installed plugin.
 *
 * <p>Status lifecycle: {@code PENDING → ACTIVE | QUARANTINED}.
 * Plugins enter {@code PENDING} on upload, transition to {@code ACTIVE}
 * after an admin activates them, or {@code QUARANTINED} if validation fails.
 */
public record PluginDto(
        String id,
        String name,
        String version,
        String status,
        String installedBy,
        Instant installedAt
) {}
