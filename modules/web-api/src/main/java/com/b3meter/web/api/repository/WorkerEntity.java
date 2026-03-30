package com.jmeternext.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code workers} table.
 *
 * <p>Status values: {@code AVAILABLE} (online, idle), {@code BUSY} (running a test),
 * {@code OFFLINE} (unreachable or de-registered).
 */
public record WorkerEntity(
        String id,
        String hostname,
        int port,
        String status,
        Instant lastHeartbeat,
        Instant registeredAt
) {}
