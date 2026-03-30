package com.jmeternext.engine.service.http;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of an HTTP request execution.
 *
 * <p>Three timing dimensions are recorded separately so that analysis tooling can
 * compute percentile breakdowns for connection establishment, server processing,
 * and total transfer independently:
 * <ul>
 *   <li>{@code connectTimeMs} — wall-clock time from initiating the TCP/TLS handshake
 *       until the connection was usable (not included in {@code latencyMs}).</li>
 *   <li>{@code latencyMs} — time from sending the first request byte to receiving the
 *       first response byte (TTFB). Excludes connection establishment.</li>
 *   <li>{@code totalTimeMs} — wall-clock time from the first call to
 *       {@link HttpClientFactory#execute} until the last byte of the response body
 *       was read. Equals {@code connectTimeMs + latencyMs + transferTimeMs}.</li>
 * </ul>
 *
 * <p>Only JDK types are used so that this record can live in the framework-free
 * {@code engine-service} module.
 *
 * @param statusCode    HTTP status code (e.g. 200, 404, 503)
 * @param protocol      actual negotiated protocol, e.g. {@code "HTTP/1.1"} or
 *                      {@code "HTTP/2"}; reflects the protocol that was actually used
 *                      after ALPN negotiation
 * @param headers       response headers; never {@code null}, may be empty
 * @param body          response body bytes; empty array (never {@code null}) when the
 *                      response has no body, or when {@code truncated} is {@code true}
 *                      and only a partial body was read
 * @param connectTimeMs time to establish the connection, in milliseconds; {@code 0}
 *                      when the connection was reused from the pool
 * @param latencyMs     time to first byte after connection, in milliseconds
 * @param totalTimeMs   total elapsed time from request start to body fully read, in
 *                      milliseconds
 * @param truncated     {@code true} if the response body was truncated because it
 *                      exceeded a configured maximum size limit
 */
public record HttpResponse(
        int statusCode,
        String protocol,
        Map<String, String> headers,
        byte[] body,
        long connectTimeMs,
        long latencyMs,
        long totalTimeMs,
        boolean truncated
) {
    /**
     * Compact canonical constructor — validates required fields and makes the
     * headers map unmodifiable.
     */
    public HttpResponse {
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(body, "body must not be null");
        headers = Collections.unmodifiableMap(headers);
    }

    /**
     * Returns {@code true} if the status code is in the 2xx range.
     *
     * @return {@code true} for HTTP 200–299
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns the response body as a UTF-8 string.
     *
     * @return body decoded as UTF-8; empty string if body is empty
     */
    public String bodyAsString() {
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns {@code true} if the server negotiated HTTP/2.
     *
     * @return {@code true} when {@code protocol} equals {@link HttpRequest#PROTOCOL_HTTP_2}
     */
    public boolean isHttp2() {
        return HttpRequest.PROTOCOL_HTTP_2.equals(protocol);
    }
}
