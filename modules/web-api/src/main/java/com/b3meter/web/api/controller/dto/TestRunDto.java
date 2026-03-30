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
 * API response representation of a test run.
 *
 * <p>Maps the {@code test_runs} table columns to a JSON-serialisable DTO.
 * {@code startedAt} and {@code endedAt} are {@code null} until the run
 * transitions to the corresponding lifecycle states.
 *
 * @param id              unique run identifier (UUID)
 * @param planId          identifier of the parent test plan
 * @param status          current lifecycle status string (CREATED/RUNNING/STOPPING/STOPPED/ERROR)
 * @param startedAt       timestamp when the run transitioned to RUNNING; null before that
 * @param endedAt         timestamp when the run reached a terminal state; null before that
 * @param totalSamples    total number of samples collected so far
 * @param errorCount      number of failed samples so far
 * @param ownerId         identifier of the owner who started the run
 */
public record TestRunDto(
        String id,
        String planId,
        String status,
        Instant startedAt,
        Instant endedAt,
        long totalSamples,
        long errorCount,
        String ownerId
) {}
