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
package com.b3meter.web.api.repository;

import java.time.Instant;

/**
 * Immutable data record representing a row in the {@code test_runs} table.
 *
 * <p>Status lifecycle: {@code CREATED → RUNNING → COMPLETED | FAILED | STOPPED}.
 * {@code startedAt} and {@code endedAt} are null until the run transitions to the
 * corresponding states.
 */
public record TestRunEntity(
        String id,
        String planId,
        String status,
        Instant startedAt,
        Instant endedAt,
        long totalSamples,
        long errorCount,
        String ownerId
) {}
