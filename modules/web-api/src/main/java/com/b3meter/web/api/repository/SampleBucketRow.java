package com.jmeternext.web.api.repository;

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
