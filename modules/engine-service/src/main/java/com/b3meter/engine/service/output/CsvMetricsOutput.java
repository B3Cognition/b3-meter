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
package com.b3meter.engine.service.output;

import com.b3meter.engine.service.SampleBucket;

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
 * Writes JTL-compatible CSV output for sample buckets.
 *
 * <p>Each line contains: timestamp, label, sampleCount, errorCount,
 * avgResponseTime, minResponseTime, maxResponseTime, p90, p95, p99,
 * throughput.
 *
 * <h3>Configuration keys</h3>
 * <ul>
 *   <li>{@code csv.file} — output file path (default: {@code "results.csv"})</li>
 *   <li>{@code csv.delimiter} — field delimiter (default: {@code ","})</li>
 * </ul>
 *
 * <p>Uses {@link ReentrantLock} instead of {@code synchronized} to avoid
 * virtual-thread pinning.
 */
public final class CsvMetricsOutput implements MetricsOutput {

    private static final Logger LOG = Logger.getLogger(CsvMetricsOutput.class.getName());
    private static final String DEFAULT_FILE = "results.csv";
    private static final String DEFAULT_DELIMITER = ",";

    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile BufferedWriter writer;
    private String delimiter;

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public void start(String runId, Map<String, String> config) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        String filePath = config.getOrDefault("csv.file", DEFAULT_FILE);
        delimiter = config.getOrDefault("csv.delimiter", DEFAULT_DELIMITER);

        try {
            Path path = Path.of(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writeHeader();
            LOG.info(() -> "CSV metrics output writing to: " + filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open CSV output file: " + filePath, ex);
        }
    }

    private void writeHeader() throws IOException {
        writer.write(String.join(delimiter,
                "timestamp", "label", "sampleCount", "errorCount",
                "avgResponseTime", "minResponseTime", "maxResponseTime",
                "p90", "p95", "p99", "throughput"));
        writer.newLine();
        writer.flush();
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
                writer.write(String.join(delimiter,
                        String.valueOf(b.timestamp().toEpochMilli()),
                        escapeField(b.samplerLabel()),
                        String.valueOf(b.sampleCount()),
                        String.valueOf(b.errorCount()),
                        String.valueOf(b.avgResponseTime()),
                        String.valueOf(b.minResponseTime()),
                        String.valueOf(b.maxResponseTime()),
                        String.valueOf(b.percentile90()),
                        String.valueOf(b.percentile95()),
                        String.valueOf(b.percentile99()),
                        String.valueOf(b.samplesPerSecond())));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to write CSV samples", ex);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Escapes a CSV field value by quoting if it contains the delimiter,
     * a double quote, or a newline.
     */
    private String escapeField(String value) {
        if (value.contains(delimiter) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public void stop() {
        writeLock.lock();
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
                LOG.info("CSV metrics output closed");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to close CSV writer", ex);
        } finally {
            writeLock.unlock();
        }
    }
}
