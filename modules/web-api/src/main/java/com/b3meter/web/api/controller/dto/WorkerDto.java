package com.jmeternext.web.api.controller.dto;

import java.time.Instant;

/**
 * API response representation of a registered worker node.
 *
 * @param id            unique worker identifier (UUID)
 * @param hostname      hostname or IP address of the worker
 * @param port          RMI port the worker listens on (default 1099)
 * @param status        current status: AVAILABLE, BUSY, or OFFLINE
 * @param lastHeartbeat timestamp of the last heartbeat received; null if never seen
 * @param registeredAt  timestamp when the worker was registered
 */
public record WorkerDto(
        String id,
        String hostname,
        int port,
        String status,
        Instant lastHeartbeat,
        Instant registeredAt
) {}
