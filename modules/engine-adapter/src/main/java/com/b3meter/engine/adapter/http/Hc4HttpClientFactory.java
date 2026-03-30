package com.jmeternext.engine.adapter.http;

import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.http.HttpRequest;
import com.jmeternext.engine.service.http.HttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy HC4-style wrapper using the HC5 classic (blocking) client.
 *
 * <p>This implementation exists for backward compatibility with test plans that
 * were designed for Apache HttpComponents 4 (synchronous, one-thread-per-request).
 * It uses the same {@link HttpClientFactory} interface as {@link Hc5HttpClientFactory}
 * so that both can be injected interchangeably.
 *
 * <p>Key differences from {@link Hc5HttpClientFactory}:
 * <ul>
 *   <li>Uses the <em>classic</em> (blocking I/O) HC5 API — one thread is blocked
 *       per in-flight request. Suitable for use with virtual threads (Java 21) where
 *       blocking is cheap, but does not support HTTP/2 or ALPN negotiation.</li>
 *   <li>Always negotiates HTTP/1.1 regardless of the {@link HttpRequest#protocol()}
 *       field. If HTTP/2 is requested the response will still report
 *       {@code protocol="HTTP/1.1"}.</li>
 *   <li>Connect time is estimated as the overhead before the blocking call;
 *       latency is the time from request dispatch to first-byte available;
 *       total time covers the full execute-to-body-read duration.</li>
 * </ul>
 *
 * <p>For new test plans prefer {@link Hc5HttpClientFactory} which supports HTTP/2.
 *
 * <h2>Thread safety</h2>
 * This class is thread-safe once constructed. The underlying
 * {@link CloseableHttpClient} uses an internal connection pool.
 */
public final class Hc4HttpClientFactory implements HttpClientFactory {

    private static final Logger LOG = Logger.getLogger(Hc4HttpClientFactory.class.getName());

    /** Default socket / response timeout when the request specifies 0. */
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 30_000;

    /** Default connect timeout when the request specifies 0. */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    private final CloseableHttpClient httpClient;
    private volatile boolean closed = false;

    /**
     * Constructs an HC4-compatible factory with a pooling connection manager.
     */
    public Hc4HttpClientFactory() {
        this.httpClient = HttpClients.custom()
                .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setMaxConnPerRoute(50)
                                .setMaxConnTotal(200)
                                .build()
                )
                .build();
    }

    /**
     * Package-private constructor for testing — accepts a pre-built client.
     *
     * @param httpClient pre-configured classic HTTP client
     */
    Hc4HttpClientFactory(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // -------------------------------------------------------------------------
    // HttpClientFactory
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Executes the request on the calling thread (blocking). Returns HTTP/1.1
     * regardless of the {@code protocol} field — HC4-style clients do not support
     * ALPN or HTTP/2 negotiation.
     *
     * @throws IOException if a network, timeout, or protocol error occurs
     * @throws IllegalStateException if this factory has been closed
     */
    /**
     * {@inheritDoc}
     *
     * <p>{@code @SuppressWarnings("deprecation")}: HC5 5.3.x deprecated ALL timeout
     * setters on {@code RequestConfig.Builder} in favour of per-connection-manager
     * {@code ConnectionConfig}. Per-request timeouts are required for a load tester
     * where each sampler can specify its own connect/response timeout, so we
     * deliberately continue using {@code RequestConfig} and suppress the warning.
     */
    @SuppressWarnings("deprecation")
    @Override
    public HttpResponse execute(HttpRequest request) throws IOException {
        if (closed) {
            throw new IOException("Hc4HttpClientFactory is closed");
        }

        long startNs = System.nanoTime();

        int connectMs = request.connectTimeoutMs() > 0
                ? request.connectTimeoutMs() : DEFAULT_CONNECT_TIMEOUT_MS;
        int responseMs = request.responseTimeoutMs() > 0
                ? request.responseTimeoutMs() : DEFAULT_RESPONSE_TIMEOUT_MS;

        // Use the non-deprecated long+TimeUnit overloads
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectMs, TimeUnit.MILLISECONDS)
                .setResponseTimeout(responseMs, TimeUnit.MILLISECONDS)
                .build();

        HttpUriRequestBase hcRequest = new HttpUriRequestBase(
                request.method(), URI.create(request.url()));
        hcRequest.setConfig(requestConfig);

        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            hcRequest.addHeader(entry.getKey(), entry.getValue());
        }

        if (request.body() != null && request.body().length > 0) {
            String contentType = request.headers().getOrDefault(
                    "Content-Type", "application/octet-stream");
            hcRequest.setEntity(new ByteArrayEntity(
                    request.body(), ContentType.parse(contentType)));
        }

        long dispatchNs = System.nanoTime();

        // Use the non-deprecated execute(ClassicHttpRequest, HttpClientResponseHandler) overload
        return httpClient.execute(hcRequest, (ClassicHttpResponse hcResponse) -> {
            long firstByteNs = System.nanoTime();

            Map<String, String> headers = new HashMap<>();
            for (Header h : hcResponse.getHeaders()) {
                headers.put(h.getName(), h.getValue());
            }

            byte[] body = hcResponse.getEntity() != null
                    ? hcResponse.getEntity().getContent().readAllBytes()
                    : new byte[0];

            long endNs = System.nanoTime();

            long connectTimeMs = TimeUnit.NANOSECONDS.toMillis(dispatchNs - startNs);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(firstByteNs - dispatchNs);
            long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(endNs - startNs);

            return new HttpResponse(
                    hcResponse.getCode(),
                    HttpRequest.PROTOCOL_HTTP_1_1,   // HC4-style never negotiates h2
                    headers,
                    body,
                    connectTimeMs,
                    latencyMs,
                    totalTimeMs,
                    false
            );
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the underlying connection pool. Idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            httpClient.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error closing HC4 client", e);
        }
    }
}
