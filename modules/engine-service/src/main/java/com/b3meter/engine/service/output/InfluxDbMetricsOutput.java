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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes sample data to InfluxDB using the line protocol over HTTP.
 *
 * <p>Supports both InfluxDB v1 (query-param database) and v2 (Authorization
 * token header). Uses {@link java.net.http.HttpClient} — no external
 * dependencies.
 *
 * <h3>Configuration keys</h3>
 * <ul>
 *   <li>{@code influxdb.url} — InfluxDB write endpoint base URL
 *       (e.g. {@code "http://localhost:8086"}); required</li>
 *   <li>{@code influxdb.database} — database / bucket name
 *       (default: {@code "jmeter"})</li>
 *   <li>{@code influxdb.measurement} — measurement name
 *       (default: {@code "jmeter_results"})</li>
 *   <li>{@code influxdb.token} — optional Bearer token for InfluxDB v2</li>
 * </ul>
 *
 * <p>Writes are fire-and-forget: HTTP requests are sent asynchronously via
 * {@link HttpClient#sendAsync}. Errors are logged but do not propagate.
 */
public final class InfluxDbMetricsOutput implements MetricsOutput {

    private static final Logger LOG = Logger.getLogger(InfluxDbMetricsOutput.class.getName());
    private static final String DEFAULT_DATABASE = "jmeter";
    private static final String DEFAULT_MEASUREMENT = "jmeter_results";

    private HttpClient httpClient;
    private URI writeUri;
    private String measurement;
    private String token;
    private String runId;

    @Override
    public String name() {
        return "influxdb";
    }

    @Override
    public void start(String runId, Map<String, String> config) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        String baseUrl = config.get("influxdb.url");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("influxdb.url is required");
        }

        String database = config.getOrDefault("influxdb.database", DEFAULT_DATABASE);
        this.measurement = config.getOrDefault("influxdb.measurement", DEFAULT_MEASUREMENT);
        this.token = config.get("influxdb.token");
        this.runId = runId;

        // Build the write endpoint URI
        String separator = baseUrl.contains("?") ? "&" : "?";
        String writeUrl = baseUrl.endsWith("/")
                ? baseUrl + "write" + separator + "db=" + database
                : baseUrl + "/write" + separator + "db=" + database;
        this.writeUri = URI.create(writeUrl);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        LOG.info(() -> "InfluxDB metrics output targeting: " + writeUri);
    }

    @Override
    public void writeSamples(List<SampleBucket> samples) {
        Objects.requireNonNull(samples, "samples must not be null");
        if (samples.isEmpty() || httpClient == null) {
            return;
        }

        StringBuilder body = new StringBuilder(samples.size() * 200);
        for (SampleBucket b : samples) {
            // InfluxDB line protocol:
            // measurement,tag=value field=value timestampNs
            body.append(measurement)
                    .append(",label=").append(escapeTagValue(b.samplerLabel()))
                    .append(",runId=").append(escapeTagValue(runId))
                    .append(' ')
                    .append("sampleCount=").append(b.sampleCount()).append('i')
                    .append(",errorCount=").append(b.errorCount()).append('i')
                    .append(",avgResponseTime=").append(b.avgResponseTime())
                    .append(",minResponseTime=").append(b.minResponseTime())
                    .append(",maxResponseTime=").append(b.maxResponseTime())
                    .append(",p90=").append(b.percentile90())
                    .append(",p95=").append(b.percentile95())
                    .append(",p99=").append(b.percentile99())
                    .append(",throughput=").append(b.samplesPerSecond())
                    .append(' ')
                    .append(b.timestamp().toEpochMilli() * 1_000_000L) // ms -> ns
                    .append('\n');
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(writeUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));

        if (token != null && !token.isBlank()) {
            reqBuilder.header("Authorization", "Token " + token);
        }

        httpClient.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOG.log(Level.WARNING, "InfluxDB write failed", error);
                    } else if (response.statusCode() >= 300) {
                        LOG.warning(() -> "InfluxDB write returned HTTP " + response.statusCode()
                                + ": " + response.body());
                    }
                });
    }

    /**
     * Escapes a tag value for InfluxDB line protocol.
     * Commas, equals signs, and spaces must be backslash-escaped in tag values.
     */
    private static String escapeTagValue(String value) {
        return value.replace(" ", "\\ ")
                .replace(",", "\\,")
                .replace("=", "\\=");
    }

    @Override
    public void stop() {
        if (httpClient != null) {
            httpClient = null;
            LOG.info("InfluxDB metrics output stopped");
        }
    }
}
