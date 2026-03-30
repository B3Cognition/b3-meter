/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.web.api.controller.dto;

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
