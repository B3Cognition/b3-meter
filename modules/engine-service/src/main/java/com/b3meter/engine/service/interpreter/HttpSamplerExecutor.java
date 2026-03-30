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
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;
import com.b3meter.engine.service.plan.PlanNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code HTTPSamplerProxy} or {@code HTTPSampler} {@link PlanNode}.
 *
 * <p>Reads the standard JMeter HTTP sampler properties:
 * <ul>
 *   <li>{@code HTTPSampler.domain} — target hostname</li>
 *   <li>{@code HTTPSampler.path} — request path (default {@code /})</li>
 *   <li>{@code HTTPSampler.method} — HTTP method (default {@code GET})</li>
 *   <li>{@code HTTPSampler.port} — port (default: 80 for HTTP, 443 for HTTPS)</li>
 *   <li>{@code HTTPSampler.protocol} — {@code http} or {@code https} (default {@code http})</li>
 *   <li>{@code HTTPSampler.connect_timeout} — connect timeout in ms (0 = implementation default)</li>
 *   <li>{@code HTTPSampler.response_timeout} — response timeout in ms (0 = implementation default)</li>
 * </ul>
 *
 * <p>Variable substitution ({@code ${varName}}) is applied to domain, path, and method
 * before the request is constructed.
 *
 * <p>Only JDK types are used — no Spring, no logging frameworks beyond {@code java.util.logging}
 * (Constitution Principle I).
 */
public final class HttpSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(HttpSamplerExecutor.class.getName());

    private final HttpClientFactory httpClientFactory;

    /**
     * Constructs an executor backed by the given HTTP client factory.
     *
     * @param httpClientFactory the HTTP client; must not be {@code null}
     * @throws NullPointerException if {@code httpClientFactory} is {@code null}
     */
    public HttpSamplerExecutor(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = Objects.requireNonNull(
                httpClientFactory, "httpClientFactory must not be null");
    }

    /**
     * Executes the HTTP request described by {@code node} and returns the {@link SampleResult}.
     *
     * @param node      the HTTPSamplerProxy/HTTPSampler node; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     * @return a populated {@link SampleResult}; never {@code null}
     */
    public SampleResult execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String label = node.getTestName();
        SampleResult result = new SampleResult(label);

        // Read and resolve sampler properties
        String protocol = resolve(node.getStringProp("HTTPSampler.protocol", "http"), variables);
        String domain   = resolve(node.getStringProp("HTTPSampler.domain",   "localhost"), variables);
        String path     = resolve(node.getStringProp("HTTPSampler.path",     "/"), variables);
        String method   = resolve(node.getStringProp("HTTPSampler.method",   "GET"), variables);

        // Port handling: use explicit port or default per protocol
        int port = resolvePort(node, protocol, variables);

        String url = buildUrl(protocol, domain, port, path);

        // Request headers
        Map<String, String> headers = extractHeaders(node, variables);

        // Body (for POST/PUT/PATCH)
        byte[] body = extractBody(node, variables);

        // Timeouts
        int connectTimeout  = node.getIntProp("HTTPSampler.connect_timeout",  0);
        int responseTimeout = node.getIntProp("HTTPSampler.response_timeout", 0);

        String httpProtocol = HttpRequest.PROTOCOL_HTTP_1_1;

        HttpRequest request = new HttpRequest(
                method.toUpperCase(),
                url,
                headers,
                body,
                connectTimeout,
                responseTimeout,
                httpProtocol
        );

        LOG.log(Level.FINE, "HttpSamplerExecutor: executing {0} {1}", new Object[]{method, url});

        long start = System.currentTimeMillis();
        try {
            HttpResponse response = httpClientFactory.execute(request);
            long total = System.currentTimeMillis() - start;

            result.setStatusCode(response.statusCode());
            result.setConnectTimeMs(response.connectTimeMs());
            result.setLatencyMs(response.latencyMs());
            result.setTotalTimeMs(total);
            result.setResponseBody(response.bodyAsString());

            // Mark failure on non-2xx by default; assertions can refine this
            if (!response.isSuccess()) {
                result.setSuccess(false);
                result.setFailureMessage("HTTP " + response.statusCode() + " on " + url);
                // Reset success to false explicitly (setFailureMessage already does it,
                // but we want the message without permanently locking success to false)
                result.setSuccess(false);
            }

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("Network error: " + e.getMessage());
            LOG.log(Level.WARNING, "HttpSamplerExecutor: request failed for " + url, e);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }

    private static int resolvePort(PlanNode node, String protocol, Map<String, String> variables) {
        // Port can be stored as intProp or stringProp (JMeter stores it as string in some versions)
        int intPort = node.getIntProp("HTTPSampler.port", -1);
        if (intPort > 0) {
            return intPort;
        }
        String strPort = resolve(node.getStringProp("HTTPSampler.port", ""), variables);
        if (!strPort.isBlank()) {
            try {
                int p = Integer.parseInt(strPort.trim());
                if (p > 0) return p;
            } catch (NumberFormatException ignored) {
                // fall through to protocol defaults
            }
        }
        return "https".equalsIgnoreCase(protocol) ? 443 : 80;
    }

    private static String buildUrl(String protocol, String domain, int port, String path) {
        boolean defaultPort = ("http".equalsIgnoreCase(protocol) && port == 80)
                || ("https".equalsIgnoreCase(protocol) && port == 443);

        StringBuilder sb = new StringBuilder();
        sb.append(protocol.toLowerCase()).append("://").append(domain);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        if (!path.startsWith("/")) {
            sb.append('/');
        }
        sb.append(path);
        return sb.toString();
    }

    /**
     * Extracts any explicit request headers from the {@code HTTPSampler.HeaderManager}
     * element property, if present.
     */
    private static Map<String, String> extractHeaders(PlanNode node,
                                                       Map<String, String> variables) {
        Map<String, String> headers = new HashMap<>();
        PlanNode hm = node.getElementProp("HTTPSampler.HeaderManager");
        if (hm != null) {
            for (Object item : hm.getCollectionProp("HeaderManager.headers")) {
                if (item instanceof PlanNode header) {
                    String name  = resolve(header.getStringProp("Header.name"),  variables);
                    String value = resolve(header.getStringProp("Header.value"), variables);
                    if (name != null && !name.isBlank()) {
                        headers.put(name, value != null ? value : "");
                    }
                }
            }
        }
        return headers;
    }

    /**
     * Returns the raw post body bytes, if the sampler has a body defined.
     * Returns {@code null} for samplers without a body (GET, DELETE, etc.).
     */
    private static byte[] extractBody(PlanNode node, Map<String, String> variables) {
        String rawBody = node.getStringProp("HTTPSampler.postBodyRaw");
        if (rawBody != null && !rawBody.isBlank()) {
            String resolved = resolve(rawBody, variables);
            return resolved.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
}
