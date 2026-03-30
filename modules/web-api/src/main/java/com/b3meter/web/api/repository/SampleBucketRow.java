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
 * Immutable data record representing one aggregated time-bucket row in the
 * {@code sample_results} table.
 *
 * <p>Each row covers one discrete time slice (typically 1 second) for a single
 * sampler label within a test run.
 */
public record SampleBucketRow(
        String runId,
        Instant timestamp,
        String samplerLabel,
        long sampleCount,
        long errorCount,
        double avgResponseTime,
        double minResponseTime,
        double maxResponseTime,
        double percentile90,
        double percentile95,
        double percentile99,
        double samplesPerSecond
) {}
