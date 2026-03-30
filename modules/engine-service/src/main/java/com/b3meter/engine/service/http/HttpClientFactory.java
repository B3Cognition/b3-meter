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
package com.b3meter.engine.service.http;

import java.io.Closeable;
import java.io.IOException;

/**
 * Port (interface) for executing HTTP requests.
 *
 * <p>Lives in {@code engine-service} so that the pure-Java engine layer can depend on
 * it without importing any concrete HTTP library. Concrete implementations (HC5, HC4)
 * live in {@code engine-adapter} and are injected at runtime.
 *
 * <p>This design satisfies the Engine-First Decoupling principle (Constitution
 * Principle I): {@code engine-service} must remain framework-free and library-free.
 * Only JDK types appear in this interface.
 */
public interface HttpClientFactory extends Closeable {

    /**
     * Executes a single HTTP request and returns the full response.
     *
     * <p>Implementations are responsible for:
     * <ul>
     *   <li>Negotiating the protocol version (HTTP/1.1 vs HTTP/2 via ALPN)</li>
     *   <li>Recording {@code connectTimeMs}, {@code latencyMs} (TTFB), and
     *       {@code totalTimeMs} separately</li>
     *   <li>Honouring the {@code connectTimeoutMs} and {@code responseTimeoutMs}
     *       specified in the request</li>
     * </ul>
     *
     * @param request the HTTP request descriptor; must not be {@code null}
     * @return the HTTP response including timing information
     * @throws IOException if a network error, timeout, or protocol failure occurs
     */
    HttpResponse execute(HttpRequest request) throws IOException;

    /**
     * Releases all connection-pool resources held by this factory.
     *
     * <p>After closing, any call to {@link #execute} must throw {@link IOException}.
     * Implementations must be idempotent — calling {@code close()} twice must not
     * throw.
     */
    @Override
    void close();
}
