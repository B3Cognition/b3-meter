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
package com.b3meter.engine.adapter;

import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A sampler that executes real HTTP requests using an {@link HttpClientFactory}.
 *
 * <p>Each call to {@link #execute()} performs an HTTP GET to the configured target URL
 * and returns a {@link SampleResult} containing the response status, body bytes,
 * and timing measurements (connect time, latency, total time).
 *
 * <p>If the HTTP request fails (network error, timeout, connection refused), the
 * returned {@link SampleResult} has {@code success=false} and records the elapsed
 * wall-clock time up to the point of failure.
 *
 * <p>Instances are thread-safe because the underlying {@link HttpClientFactory} is
 * thread-safe and all instance fields are effectively final.
 */
public final class SimulatedSampler {

    private static final Logger LOG = Logger.getLogger(SimulatedSampler.class.getName());

    /** Default label reported in {@link SampleResult#label()} when none is specified. */
    public static final String DEFAULT_LABEL = "HTTP Request";

    private final HttpClientFactory httpClientFactory;
    private final String url;
    private final String label;

    /**
     * Constructs a sampler that will GET the given {@code url}.
     *
     * @param httpClientFactory the HTTP client to use for execution; must not be {@code null}
     * @param url               absolute URL to GET; must not be {@code null}
     * @param label             sampler label used in result reporting; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SimulatedSampler(HttpClientFactory httpClientFactory, String url, String label) {
        this.httpClientFactory = Objects.requireNonNull(httpClientFactory, "httpClientFactory must not be null");
        this.url   = Objects.requireNonNull(url,   "url must not be null");
        this.label = Objects.requireNonNull(label, "label must not be null");
    }

    /**
     * Convenience constructor that uses {@link #DEFAULT_LABEL}.
     *
     * @param httpClientFactory the HTTP client to use; must not be {@code null}
     * @param url               absolute URL to GET; must not be {@code null}
     */
    public SimulatedSampler(HttpClientFactory httpClientFactory, String url) {
        this(httpClientFactory, url, DEFAULT_LABEL);
    }

    /**
     * Executes one HTTP GET request against the configured URL and returns a result.
     *
     * <p>On success the result carries:
     * <ul>
     *   <li>{@code success=true} when the HTTP status code is 2xx</li>
     *   <li>{@code connectTimeMs}, {@code latencyMs}, and {@code totalTimeMs} from the response</li>
     *   <li>{@code responseBytes} with the raw body length</li>
     * </ul>
     *
     * <p>On failure (IOException) the result carries {@code success=false} and {@code totalTimeMs}
     * set to the elapsed wall-clock time up to the point of failure.
     *
     * @return a non-null {@link SampleResult}
     */
    public SampleResult execute() {
        HttpRequest request = HttpRequest.get(url);
        long startNs = System.nanoTime();

        try {
            HttpResponse response = httpClientFactory.execute(request);
            boolean success = response.isSuccess();
            if (!success) {
                LOG.log(Level.FINE, "Sampler [{0}] received non-2xx status {1} from {2}",
                        new Object[]{label, response.statusCode(), url});
            }
            return new SampleResult(
                    label,
                    success,
                    response.statusCode(),
                    response.connectTimeMs(),
                    response.latencyMs(),
                    response.totalTimeMs(),
                    response.body() != null ? response.body().length : 0
            );
        } catch (IOException ex) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            LOG.log(Level.WARNING, "Sampler [{0}] HTTP request failed for URL {1}: {2}",
                    new Object[]{label, url, ex.getMessage()});
            return new SampleResult(
                    label,
                    false,
                    0,
                    0L,
                    0L,
                    elapsedMs,
                    0
            );
        }
    }

    /** Returns the sampler label. */
    public String getLabel() {
        return label;
    }

    /** Returns the target URL. */
    public String getUrl() {
        return url;
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Immutable result of a single sampler execution.
     *
     * @param label          sampler label
     * @param success        {@code true} if the sampler completed with a 2xx response
     * @param statusCode     HTTP status code; 0 on connection failure
     * @param connectTimeMs  time to establish the TCP/TLS connection, in ms
     * @param latencyMs      time from first request byte to first response byte (TTFB), in ms
     * @param totalTimeMs    wall-clock duration from execute() entry to body fully read, in ms
     * @param responseBytes  number of response body bytes received
     */
    public record SampleResult(
            String label,
            boolean success,
            int statusCode,
            long connectTimeMs,
            long latencyMs,
            long totalTimeMs,
            int responseBytes
    ) {}
}
