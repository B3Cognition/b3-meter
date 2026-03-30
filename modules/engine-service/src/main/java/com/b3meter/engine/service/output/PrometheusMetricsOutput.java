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
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exposes metrics via a lightweight HTTP endpoint for Prometheus scraping.
 *
 * <p>Uses the JDK built-in {@link com.sun.net.httpserver.HttpServer} — no
 * external dependencies. Metrics are exposed in Prometheus text exposition
 * format at {@code /metrics}.
 *
 * <h3>Configuration keys</h3>
 * <ul>
 *   <li>{@code prometheus.port} — HTTP port to bind (default: {@code 9270})</li>
 * </ul>
 *
 * <h3>Exposed gauges</h3>
 * <ul>
 *   <li>{@code jmeter_samples_total} — cumulative sample count per label</li>
 *   <li>{@code jmeter_errors_total} — cumulative error count per label</li>
 *   <li>{@code jmeter_response_time_avg} — latest average response time per label</li>
 *   <li>{@code jmeter_response_time_p95} — latest 95th percentile per label</li>
 *   <li>{@code jmeter_throughput} — latest throughput per label</li>
 * </ul>
 *
 * <p>This output uses a pull model: Prometheus scrapes the {@code /metrics}
 * endpoint. {@link #writeSamples} updates the in-memory gauge values atomically.
 */
public final class PrometheusMetricsOutput implements MetricsOutput {

    private static final Logger LOG = Logger.getLogger(PrometheusMetricsOutput.class.getName());
    private static final int DEFAULT_PORT = 9270;

    private final ConcurrentHashMap<String, LabelMetrics> metricsMap = new ConcurrentHashMap<>();
    private final ReentrantLock serverLock = new ReentrantLock();
    private volatile HttpServer server;
    private String runId;

    /**
     * Holds the latest metric values for a single sampler label.
     */
    private static final class LabelMetrics {
        volatile long samplesTotal;
        volatile long errorsTotal;
        volatile double avgResponseTime;
        volatile double p95;
        volatile double throughput;
    }

    @Override
    public String name() {
        return "prometheus";
    }

    @Override
    public void start(String runId, Map<String, String> config) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        this.runId = runId;
        int port = DEFAULT_PORT;
        String portStr = config.get("prometheus.port");
        if (portStr != null && !portStr.isBlank()) {
            port = Integer.parseInt(portStr);
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", exchange -> {
                String body = buildMetricsBody();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",
                        "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            server.setExecutor(null); // default executor
            server.start();

            int finalPort = port;
            LOG.info(() -> "Prometheus metrics endpoint listening on port " + finalPort);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start Prometheus HTTP server on port " + port, ex);
        }
    }

    @Override
    public void writeSamples(List<SampleBucket> samples) {
        Objects.requireNonNull(samples, "samples must not be null");
        for (SampleBucket b : samples) {
            LabelMetrics m = metricsMap.computeIfAbsent(b.samplerLabel(), k -> new LabelMetrics());
            m.samplesTotal += b.sampleCount();
            m.errorsTotal += b.errorCount();
            m.avgResponseTime = b.avgResponseTime();
            m.p95 = b.percentile95();
            m.throughput = b.samplesPerSecond();
        }
    }

    /**
     * Builds the Prometheus text exposition format body from current metrics.
     */
    private String buildMetricsBody() {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("# HELP jmeter_samples_total Total number of samples.\n");
        sb.append("# TYPE jmeter_samples_total counter\n");
        for (var entry : metricsMap.entrySet()) {
            sb.append("jmeter_samples_total{label=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\",run_id=\"").append(escapeLabel(runId))
                    .append("\"} ").append(entry.getValue().samplesTotal).append('\n');
        }

        sb.append("# HELP jmeter_errors_total Total number of errors.\n");
        sb.append("# TYPE jmeter_errors_total counter\n");
        for (var entry : metricsMap.entrySet()) {
            sb.append("jmeter_errors_total{label=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\",run_id=\"").append(escapeLabel(runId))
                    .append("\"} ").append(entry.getValue().errorsTotal).append('\n');
        }

        sb.append("# HELP jmeter_response_time_avg Average response time in milliseconds.\n");
        sb.append("# TYPE jmeter_response_time_avg gauge\n");
        for (var entry : metricsMap.entrySet()) {
            sb.append("jmeter_response_time_avg{label=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\",run_id=\"").append(escapeLabel(runId))
                    .append("\"} ").append(entry.getValue().avgResponseTime).append('\n');
        }

        sb.append("# HELP jmeter_response_time_p95 95th percentile response time in milliseconds.\n");
        sb.append("# TYPE jmeter_response_time_p95 gauge\n");
        for (var entry : metricsMap.entrySet()) {
            sb.append("jmeter_response_time_p95{label=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\",run_id=\"").append(escapeLabel(runId))
                    .append("\"} ").append(entry.getValue().p95).append('\n');
        }

        sb.append("# HELP jmeter_throughput Current throughput in samples per second.\n");
        sb.append("# TYPE jmeter_throughput gauge\n");
        for (var entry : metricsMap.entrySet()) {
            sb.append("jmeter_throughput{label=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\",run_id=\"").append(escapeLabel(runId))
                    .append("\"} ").append(entry.getValue().throughput).append('\n');
        }

        return sb.toString();
    }

    /**
     * Escapes a label value for Prometheus text format.
     * Backslashes, double quotes, and newlines must be escaped.
     */
    private static String escapeLabel(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    @Override
    public void stop() {
        serverLock.lock();
        try {
            if (server != null) {
                server.stop(1); // 1-second drain delay
                server = null;
                metricsMap.clear();
                LOG.info("Prometheus metrics endpoint stopped");
            }
        } finally {
            serverLock.unlock();
        }
    }
}
