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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code GrpcSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code GrpcSampler.host} — target hostname</li>
 *   <li>{@code GrpcSampler.port} — target port (default 443)</li>
 *   <li>{@code GrpcSampler.service} — fully qualified gRPC service name</li>
 *   <li>{@code GrpcSampler.method} — gRPC method name</li>
 *   <li>{@code GrpcSampler.requestBody} — JSON representation of protobuf message</li>
 *   <li>{@code GrpcSampler.useTls} — whether to use TLS (default true)</li>
 * </ul>
 *
 * <p>This is a simplified gRPC implementation that sends an HTTP/2 POST to the
 * gRPC endpoint with a length-prefixed frame (5-byte header: 1 byte compressed
 * flag + 4 byte big-endian length + JSON body as bytes). This approach works
 * with gRPC servers that support JSON transcoding or gRPC-Web.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class GrpcSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(GrpcSamplerExecutor.class.getName());

    private static final int DEFAULT_PORT = 443;

    private GrpcSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the gRPC operation described by {@code node}.
     *
     * @param node              the GrpcSampler node; must not be {@code null}
     * @param result            the sample result to populate; must not be {@code null}
     * @param variables         current VU variable scope for {@code ${varName}} substitution
     * @param httpClientFactory HTTP client for the underlying HTTP/2 request
     */
    public static void execute(PlanNode node, SampleResult result,
                               Map<String, String> variables,
                               HttpClientFactory httpClientFactory) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");
        Objects.requireNonNull(httpClientFactory, "httpClientFactory must not be null");

        String host = resolve(node.getStringProp("GrpcSampler.host", ""), variables);
        int port = node.getIntProp("GrpcSampler.port", DEFAULT_PORT);
        String service = resolve(node.getStringProp("GrpcSampler.service", ""), variables);
        String method = resolve(node.getStringProp("GrpcSampler.method", ""), variables);
        String requestBody = resolve(node.getStringProp("GrpcSampler.requestBody", ""), variables);
        boolean useTls = node.getBoolProp("GrpcSampler.useTls", true);

        if (host.isBlank()) {
            result.setFailureMessage("GrpcSampler.host is empty");
            return;
        }

        if (service.isBlank() || method.isBlank()) {
            result.setFailureMessage("GrpcSampler.service and GrpcSampler.method are required");
            return;
        }

        // Build gRPC path: /fully.qualified.ServiceName/MethodName
        String path = "/" + service + "/" + method;
        String protocol = useTls ? "https" : "http";
        String url = buildUrl(protocol, host, port, path);

        LOG.log(Level.FINE, "GrpcSamplerExecutor: POST {0}", url);

        // Build length-prefixed gRPC frame
        byte[] body = buildGrpcFrame(requestBody);

        // gRPC headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/grpc");
        headers.put("TE", "trailers");
        headers.put("grpc-encoding", "identity");
        headers.put("grpc-accept-encoding", "identity");

        HttpRequest request = new HttpRequest(
                "POST",
                url,
                headers,
                body,
                0, // connect timeout — use client default
                0, // response timeout — use client default
                HttpRequest.PROTOCOL_HTTP_2
        );

        long start = System.currentTimeMillis();
        try {
            HttpResponse response = httpClientFactory.execute(request);
            long total = System.currentTimeMillis() - start;

            result.setConnectTimeMs(response.connectTimeMs());
            result.setLatencyMs(response.latencyMs());
            result.setTotalTimeMs(total);

            // Extract gRPC status from response
            int grpcStatus = extractGrpcStatus(response);
            result.setStatusCode(grpcStatus);

            // Decode response body (skip 5-byte length-prefix if present)
            String responseBody = decodeGrpcResponse(response.body());
            result.setResponseBody(responseBody);

            if (grpcStatus != 0) {
                result.setFailureMessage("gRPC status " + grpcStatus + " on " + url);
            }

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("gRPC error: " + e.getMessage());
            LOG.log(Level.WARNING, "GrpcSamplerExecutor: request failed for " + url, e);
        }
    }

    // =========================================================================
    // gRPC frame encoding/decoding (package-visible for testing)
    // =========================================================================

    /**
     * Builds a gRPC length-prefixed frame.
     *
     * <p>Format: 1 byte compression flag (0 = none) + 4 bytes big-endian message length +
     * message bytes.
     *
     * @param jsonBody the JSON body to encode
     * @return the length-prefixed frame
     */
    static byte[] buildGrpcFrame(String jsonBody) {
        byte[] messageBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream(5 + messageBytes.length);

        // Compression flag: 0 = no compression
        frame.write(0x00);
        // Message length (4 bytes, big-endian)
        frame.write((messageBytes.length >> 24) & 0xFF);
        frame.write((messageBytes.length >> 16) & 0xFF);
        frame.write((messageBytes.length >> 8) & 0xFF);
        frame.write(messageBytes.length & 0xFF);
        // Message bytes
        frame.writeBytes(messageBytes);

        return frame.toByteArray();
    }

    /**
     * Decodes a gRPC response body by stripping the 5-byte length-prefix header.
     *
     * @param body the raw response body
     * @return the decoded message as UTF-8 string
     */
    static String decodeGrpcResponse(byte[] body) {
        if (body == null || body.length < 5) {
            return (body != null) ? new String(body, StandardCharsets.UTF_8) : "";
        }

        // Skip 5-byte header (1 byte flag + 4 bytes length)
        int messageLength = ((body[1] & 0xFF) << 24)
                | ((body[2] & 0xFF) << 16)
                | ((body[3] & 0xFF) << 8)
                | (body[4] & 0xFF);

        int available = body.length - 5;
        int readLength = Math.min(messageLength, available);

        return new String(body, 5, readLength, StandardCharsets.UTF_8);
    }

    /**
     * Extracts the gRPC status code from the response.
     *
     * <p>gRPC status is typically sent as a {@code grpc-status} trailer/header.
     * Falls back to mapping HTTP status codes to gRPC status codes if the header
     * is not present.
     *
     * @param response the HTTP response
     * @return gRPC status code (0 = OK)
     */
    static int extractGrpcStatus(HttpResponse response) {
        // Check for grpc-status header
        String grpcStatusHeader = response.headers().get("grpc-status");
        if (grpcStatusHeader != null) {
            try {
                return Integer.parseInt(grpcStatusHeader.trim());
            } catch (NumberFormatException ignored) {
                // fall through to HTTP status mapping
            }
        }

        // Map HTTP status to gRPC status
        int httpStatus = response.statusCode();
        if (httpStatus == 200) return 0;          // OK
        if (httpStatus == 400) return 3;          // INVALID_ARGUMENT
        if (httpStatus == 401) return 16;         // UNAUTHENTICATED
        if (httpStatus == 403) return 7;          // PERMISSION_DENIED
        if (httpStatus == 404) return 5;          // NOT_FOUND
        if (httpStatus == 408) return 4;          // DEADLINE_EXCEEDED
        if (httpStatus == 409) return 10;         // ABORTED
        if (httpStatus == 429) return 8;          // RESOURCE_EXHAUSTED
        if (httpStatus == 500) return 13;         // INTERNAL
        if (httpStatus == 501) return 12;         // UNIMPLEMENTED
        if (httpStatus == 503) return 14;         // UNAVAILABLE
        if (httpStatus == 504) return 4;          // DEADLINE_EXCEEDED
        return 2;                                  // UNKNOWN
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String buildUrl(String protocol, String host, int port, String path) {
        boolean defaultPort = ("http".equals(protocol) && port == 80)
                || ("https".equals(protocol) && port == 443);

        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        sb.append(path);
        return sb.toString();
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
