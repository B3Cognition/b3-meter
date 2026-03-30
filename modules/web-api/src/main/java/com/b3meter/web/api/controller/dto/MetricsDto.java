package com.jmeternext.web.api.controller.dto;

import java.time.Instant;

/**
 * API response carrying the latest 1-second aggregated metrics for a run.
 *
 * <p>Populated from the most-recent {@link com.jmeternext.engine.service.SampleBucket}
 * emitted by the broker. All response-time fields are in milliseconds.
 * Returns zeroed values when no samples have been collected yet.
 *
 * @param runId           the run this snapshot belongs to
 * @param timestamp       start of the 1-second window; null if no samples yet
 * @param samplerLabel    label of the sampler; null if no samples yet
 * @param sampleCount     number of samples in the latest window
 * @param errorCount      number of error samples in the latest window
 * @param avgResponseTime mean response time in ms
 * @param minResponseTime minimum response time in ms
 * @param maxResponseTime maximum response time in ms
 * @param percentile90    90th-percentile response time in ms
 * @param percentile95    95th-percentile response time in ms
 * @param percentile99    99th-percentile response time in ms
 * @param samplesPerSecond throughput (samples/s) in the latest window
 * @param errorPercent    error rate as a percentage (0–100)
 */
public record MetricsDto(
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
        double samplesPerSecond,
        double errorPercent
) {

    /**
     * Returns a zeroed {@code MetricsDto} representing a run with no samples yet.
     *
     * @param runId the run identifier
     * @return a metrics snapshot with all numeric fields set to zero
     */
    public static MetricsDto empty(String runId) {
        return new MetricsDto(runId, null, null, 0L, 0L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
