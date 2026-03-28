package com.jmeternext.engine.service.output;

import com.jmeternext.engine.service.SampleBucket;

import java.util.List;
import java.util.Map;

/**
 * Interface for metrics output backends.
 *
 * <p>Each implementation writes sample data to a specific backend (CSV file,
 * InfluxDB, Prometheus endpoint, etc.). The {@link MetricsOutputManager} fans
 * out aggregated buckets to all registered outputs.
 *
 * <p>This interface is framework-free — implementations must use only JDK types
 * (Constitution Principle I).
 *
 * <p>Implementations must be thread-safe. {@link #writeSamples} may be called
 * from any thread and must not block the caller for extended periods.
 */
public interface MetricsOutput {

    /**
     * Returns the short name identifying this output backend.
     *
     * <p>Used in log messages and configuration keys. Examples: {@code "csv"},
     * {@code "influxdb"}, {@code "prometheus"}, {@code "json"}.
     *
     * @return a non-null, non-empty name
     */
    String name();

    /**
     * Initialises the output backend for the given test run.
     *
     * <p>Called once before any samples are written. Implementations should
     * open files, establish connections, or bind ports as needed.
     *
     * @param runId  the unique test-run identifier; never {@code null}
     * @param config backend-specific configuration; never {@code null}
     */
    void start(String runId, Map<String, String> config);

    /**
     * Writes a batch of sample buckets to the backend.
     *
     * <p>This method must be non-blocking — implementations that perform I/O
     * should buffer internally or use asynchronous writes. A failure in one
     * output must not affect other outputs managed by the same
     * {@link MetricsOutputManager}.
     *
     * @param samples the sample buckets to write; never {@code null}, may be empty
     */
    void writeSamples(List<SampleBucket> samples);

    /**
     * Stops the output backend and releases resources.
     *
     * <p>Called once when the test run finishes. Implementations should flush
     * any pending data and close files/connections.
     */
    void stop();
}
