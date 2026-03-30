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

import java.util.List;

/**
 * Request body for starting a new test run.
 *
 * <p>Carries the plan identifier and optional execution parameters. When
 * {@code virtualUsers} or {@code durationSeconds} are {@code null} the
 * service will apply sensible defaults (1 virtual user, 0 = run until plan
 * completes).
 *
 * <p>For distributed runs, {@code workerAddresses} lists the
 * {@code host:port} strings of worker nodes that should participate.
 * When {@code null} or empty the run executes locally on the controller.
 *
 * @param planId              identifier of the saved test plan to execute; must not be null or blank
 * @param virtualUsers        number of concurrent virtual-user threads; null means default (1)
 * @param durationSeconds     maximum run duration in seconds; null means run until plan completes
 * @param workerAddresses     list of worker {@code host:port} addresses for distributed execution; null means local
 * @param slaP95Ms            SLA threshold: p95 response time must stay below this (ms); null means no check
 * @param slaP99Ms            SLA threshold: p99 response time must stay below this (ms); null means no check
 * @param slaAvgMs            SLA threshold: avg response time must stay below this (ms); null means no check
 * @param slaMaxErrorPercent  SLA threshold: error rate must stay below this (%); null means no check
 */
public record StartRunRequest(
        String planId,
        Integer virtualUsers,
        Long durationSeconds,
        List<String> workerAddresses,
        Double slaP95Ms,
        Double slaP99Ms,
        Double slaAvgMs,
        Double slaMaxErrorPercent
) {}
