package com.jmeternext.engine.service.output;

import com.jmeternext.engine.service.SampleBucket;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes sample data as NDJSON (Newline-Delimited JSON).
 *
 * <p>Each {@link SampleBucket} is serialised as a single JSON object on one
 * line. Uses manual JSON construction — no external serialisation library
 * required (Constitution Principle I).
 *
 * <h3>Configuration keys</h3>
 * <ul>
 *   <li>{@code json.file} — output file path (default: {@code "results.jsonl"})</li>
 * </ul>
 *
 * <p>Uses {@link ReentrantLock} instead of {@code synchronized} to avoid
 * virtual-thread pinning.
 */
public final class JsonMetricsOutput implements MetricsOutput {

    private static final Logger LOG = Logger.getLogger(JsonMetricsOutput.class.getName());
    private static final String DEFAULT_FILE = "results.jsonl";

    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile BufferedWriter writer;

    @Override
    public String name() {
        return "json";
    }

    @Override
    public void start(String runId, Map<String, String> config) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        String filePath = config.getOrDefault("json.file", DEFAULT_FILE);

        try {
            Path path = Path.of(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOG.info(() -> "JSON metrics output writing to: " + filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open JSON output file: " + filePath, ex);
        }
    }

    @Override
    public void writeSamples(List<SampleBucket> samples) {
        Objects.requireNonNull(samples, "samples must not be null");
        if (samples.isEmpty() || writer == null) {
            return;
        }

        writeLock.lock();
        try {
            for (SampleBucket b : samples) {
                writer.write(toJson(b));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to write JSON samples", ex);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Serialises a {@link SampleBucket} to a single-line JSON string.
     *
     * <p>All fields are simple types (long, double, String, Instant) so
     * manual construction is straightforward and avoids external dependencies.
     */
    private static String toJson(SampleBucket b) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"timestamp\":").append(b.timestamp().toEpochMilli());
        sb.append(",\"timestampIso\":\"").append(b.timestamp()).append('"');
        sb.append(",\"label\":\"").append(escapeJsonString(b.samplerLabel())).append('"');
        sb.append(",\"sampleCount\":").append(b.sampleCount());
        sb.append(",\"errorCount\":").append(b.errorCount());
        sb.append(",\"avgResponseTime\":").append(b.avgResponseTime());
        sb.append(",\"minResponseTime\":").append(b.minResponseTime());
        sb.append(",\"maxResponseTime\":").append(b.maxResponseTime());
        sb.append(",\"p90\":").append(b.percentile90());
        sb.append(",\"p95\":").append(b.percentile95());
        sb.append(",\"p99\":").append(b.percentile99());
        sb.append(",\"throughput\":").append(b.samplesPerSecond());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Escapes a string value for JSON.
     * Handles backslash, double quote, newline, carriage return, and tab.
     */
    private static String escapeJsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void stop() {
        writeLock.lock();
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
                LOG.info("JSON metrics output closed");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to close JSON writer", ex);
        } finally {
            writeLock.unlock();
        }
    }
}
