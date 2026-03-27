package com.jmeternext.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code plugins} table.
 *
 * <p>Status lifecycle: {@code PENDING → ACTIVE | QUARANTINED}.
 * Plugins are created in {@code PENDING} state on upload; an admin must
 * explicitly activate them or the system may quarantine them on validation failure.
 */
public record PluginEntity(
        String id,
        String name,
        String version,
        String jarPath,
        String status,
        String installedBy,
        Instant installedAt
) {}
