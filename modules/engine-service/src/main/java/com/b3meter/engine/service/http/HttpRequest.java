package com.jmeternext.engine.service.http;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable descriptor for an outbound HTTP request.
 *
 * <p>Uses a Java 16+ {@code record} so all fields are final and {@link #equals},
 * {@link #hashCode}, and {@link #toString} are generated automatically. Only JDK
 * types are used so that this record can live in the framework-free
 * {@code engine-service} module.
 *
 * <p>Protocol constants:
 * <ul>
 *   <li>{@link #PROTOCOL_HTTP_1_1} — force HTTP/1.1 even when the server supports h2</li>
 *   <li>{@link #PROTOCOL_HTTP_2} — prefer HTTP/2 via ALPN; fall back to HTTP/1.1</li>
 * </ul>
 *
 * @param method             HTTP method (GET, POST, PUT, DELETE, …); never {@code null}
 * @param url                absolute URL; never {@code null}
 * @param headers            request headers; never {@code null}, may be empty
 * @param body               request body bytes; {@code null} for requests with no body
 * @param connectTimeoutMs   maximum time to establish the TCP connection, in milliseconds;
 *                           {@code 0} means use the implementation default
 * @param responseTimeoutMs  maximum time to wait for the first response byte (TTFB), in
 *                           milliseconds; {@code 0} means use the implementation default
 * @param protocol           desired protocol version; one of {@link #PROTOCOL_HTTP_1_1}
 *                           or {@link #PROTOCOL_HTTP_2}; never {@code null}
 */
public record HttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        byte[] body,
        int connectTimeoutMs,
        int responseTimeoutMs,
        String protocol
) {
    /** Constant for forcing HTTP/1.1. */
    public static final String PROTOCOL_HTTP_1_1 = "HTTP/1.1";

    /** Constant for preferring HTTP/2 with ALPN negotiation. */
    public static final String PROTOCOL_HTTP_2 = "HTTP/2";

    /**
     * Compact canonical constructor — validates required fields and makes the
     * headers map unmodifiable so callers cannot mutate it after construction.
     */
    public HttpRequest {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
        headers = Collections.unmodifiableMap(headers);
    }

    /**
     * Convenience factory for a GET request with default timeouts and HTTP/1.1.
     *
     * @param url target URL
     * @return a GET request with empty headers and no body
     */
    public static HttpRequest get(String url) {
        return new HttpRequest("GET", url, Map.of(), null, 0, 0, PROTOCOL_HTTP_1_1);
    }

    /**
     * Convenience factory for a GET request that prefers HTTP/2.
     *
     * @param url target URL
     * @return a GET request with empty headers, no body, and protocol=HTTP/2
     */
    public static HttpRequest getH2(String url) {
        return new HttpRequest("GET", url, Map.of(), null, 0, 0, PROTOCOL_HTTP_2);
    }
}
