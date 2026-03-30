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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.plan.PlanNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code SSESampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code SSESampler.url} — HTTP/HTTPS URL of the SSE endpoint</li>
 *   <li>{@code SSESampler.duration} — time in ms to listen for events (default 5000)</li>
 *   <li>{@code SSESampler.eventName} — optional event name filter; empty = accept all</li>
 * </ul>
 *
 * <p>Opens an HTTP connection with {@code Accept: text/event-stream}, reads SSE events
 * for the configured duration, collects them in the response body (newline-separated),
 * and sets the HTTP status code as responseCode.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SSESamplerExecutor {

    private static final Logger LOG = Logger.getLogger(SSESamplerExecutor.class.getName());

    private static final int DEFAULT_DURATION_MS = 5000;

    private SSESamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the SSE operation described by {@code node}.
     *
     * @param node            the SSESampler node; must not be {@code null}
     * @param result          the sample result to populate; must not be {@code null}
     * @param variables       current VU variable scope for {@code ${varName}} substitution
     * @param httpClientFactory unused — SSE uses raw HttpURLConnection for streaming;
     *                          accepted for API consistency with the dispatcher
     */
    public static void execute(PlanNode node, SampleResult result,
                               Map<String, String> variables,
                               HttpClientFactory httpClientFactory) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String url = resolve(node.getStringProp("SSESampler.url", ""), variables);
        int duration = node.getIntProp("SSESampler.duration", DEFAULT_DURATION_MS);
        String eventFilter = resolve(node.getStringProp("SSESampler.eventName", ""), variables);

        if (url.isBlank()) {
            result.setFailureMessage("SSESampler.url is empty");
            return;
        }

        LOG.log(Level.FINE, "SSESamplerExecutor: connecting to {0} for {1}ms",
                new Object[]{url, duration});

        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(Math.min(duration, 10000));
            connection.setReadTimeout(duration + 1000); // slight buffer beyond duration
            connection.setDoInput(true);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            int httpStatus = connection.getResponseCode();
            result.setStatusCode(httpStatus);
            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            if (httpStatus < 200 || httpStatus >= 300) {
                long total = System.currentTimeMillis() - start;
                result.setTotalTimeMs(total);
                result.setFailureMessage("SSE HTTP " + httpStatus + " on " + url);
                return;
            }

            // Read SSE stream for 'duration' ms
            StringBuilder collected = new StringBuilder();
            int eventCount = 0;
            long deadline = start + duration;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                String currentEvent = "";
                StringBuilder dataBuffer = new StringBuilder();

                String line;
                while (System.currentTimeMillis() < deadline) {
                    // Check if data is available or we've timed out
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {
                        // Read timeout — normal end of duration
                        break;
                    }

                    if (line == null) {
                        // Stream closed by server
                        break;
                    }

                    if (line.isEmpty()) {
                        // Empty line = end of event
                        if (dataBuffer.length() > 0) {
                            // Apply event name filter
                            if (eventFilter.isEmpty() || eventFilter.equals(currentEvent)) {
                                if (collected.length() > 0) {
                                    collected.append('\n');
                                }
                                if (!currentEvent.isEmpty()) {
                                    collected.append("event: ").append(currentEvent).append('\n');
                                }
                                collected.append("data: ").append(dataBuffer);
                                eventCount++;
                            }
                            dataBuffer.setLength(0);
                            currentEvent = "";
                        }
                    } else if (line.startsWith("data:")) {
                        String data = line.length() > 5 ? line.substring(5).stripLeading() : "";
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append('\n');
                        }
                        dataBuffer.append(data);
                    } else if (line.startsWith("event:")) {
                        currentEvent = line.length() > 6 ? line.substring(6).stripLeading() : "";
                    } else if (line.startsWith("id:")) {
                        // SSE id field — acknowledged but not stored
                    } else if (line.startsWith("retry:")) {
                        // SSE retry field — acknowledged but not acted on
                    }
                    // Lines starting with ':' are comments — silently ignored
                }

                // Flush any remaining partial event
                if (dataBuffer.length() > 0) {
                    if (eventFilter.isEmpty() || eventFilter.equals(currentEvent)) {
                        if (collected.length() > 0) {
                            collected.append('\n');
                        }
                        if (!currentEvent.isEmpty()) {
                            collected.append("event: ").append(currentEvent).append('\n');
                        }
                        collected.append("data: ").append(dataBuffer);
                        eventCount++;
                    }
                }
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setResponseBody(collected.toString());

            LOG.log(Level.FINE, "SSESamplerExecutor: received {0} events in {1}ms",
                    new Object[]{eventCount, total});

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("SSE error: " + e.getMessage());
            LOG.log(Level.WARNING, "SSESamplerExecutor: error for " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
