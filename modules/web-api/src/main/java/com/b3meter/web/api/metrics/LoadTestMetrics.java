package com.jmeternext.web.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom Micrometer metrics for load-test observability.
 *
 * <p>Registers counters, gauges, and distribution summaries that are automatically
 * scraped by the Prometheus endpoint at {@code /actuator/prometheus}.
 *
 * <ul>
 *   <li>{@code jmeter_runs_total} &mdash; total test runs started (counter)</li>
 *   <li>{@code jmeter_samples_total} &mdash; total samples collected across all runs (counter)</li>
 *   <li>{@code jmeter_errors_total} &mdash; total sample errors (counter)</li>
 *   <li>{@code jmeter_active_runs} &mdash; currently active test runs (gauge)</li>
 *   <li>{@code jmeter_active_workers} &mdash; currently connected distributed workers (gauge)</li>
 *   <li>{@code jmeter_response_time_seconds} &mdash; response-time distribution (histogram/summary)</li>
 * </ul>
 */
@Component
public class LoadTestMetrics {

    private final Counter runsTotal;
    private final Counter samplesTotal;
    private final Counter errorsTotal;
    private final AtomicInteger activeRuns = new AtomicInteger(0);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final DistributionSummary responseTime;

    public LoadTestMetrics(MeterRegistry registry) {
        this.runsTotal = Counter.builder("jmeter_runs_total")
                .description("Total number of test runs started")
                .register(registry);

        this.samplesTotal = Counter.builder("jmeter_samples_total")
                .description("Total number of samples collected")
                .register(registry);

        this.errorsTotal = Counter.builder("jmeter_errors_total")
                .description("Total number of sample errors")
                .register(registry);

        registry.gauge("jmeter_active_runs",
                activeRuns,
                AtomicInteger::get);

        registry.gauge("jmeter_active_workers",
                activeWorkers,
                AtomicInteger::get);

        this.responseTime = DistributionSummary.builder("jmeter_response_time_seconds")
                .description("Response time distribution in seconds")
                .baseUnit("seconds")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    // ---- Run lifecycle ----

    /** Increment when a new test run starts. */
    public void recordRunStarted() {
        runsTotal.increment();
        activeRuns.incrementAndGet();
    }

    /** Decrement when a test run completes or is stopped. */
    public void recordRunCompleted() {
        activeRuns.decrementAndGet();
    }

    // ---- Sample recording ----

    /**
     * Record a batch of samples from a {@code SampleBucket}.
     *
     * @param sampleCount   number of successful + failed samples in the bucket
     * @param errorCount    number of errors in the bucket
     * @param avgResponseMs average response time in milliseconds
     */
    public void recordSamples(long sampleCount, long errorCount, double avgResponseMs) {
        samplesTotal.increment(sampleCount);
        if (errorCount > 0) {
            errorsTotal.increment(errorCount);
        }
        if (avgResponseMs > 0) {
            responseTime.record(avgResponseMs / 1000.0); // convert ms to seconds
        }
    }

    // ---- Worker lifecycle ----

    /** Increment when a distributed worker connects. */
    public void recordWorkerConnected() {
        activeWorkers.incrementAndGet();
    }

    /** Decrement when a distributed worker disconnects. */
    public void recordWorkerDisconnected() {
        activeWorkers.decrementAndGet();
    }

    // ---- Accessors for testing ----

    public int getActiveRuns() {
        return activeRuns.get();
    }

    public int getActiveWorkers() {
        return activeWorkers.get();
    }
}
