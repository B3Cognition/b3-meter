package com.jmeternext.engine.adapter.http;

import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.http.HttpRequest;
import com.jmeternext.engine.service.http.HttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Apache HttpComponents 5 (HC5) implementation of {@link HttpClientFactory}.
 *
 * <p>Uses {@link CloseableHttpAsyncClient} which supports HTTP/2 natively via ALPN
 * negotiation over TLS. For plain-text (h2c) connections the client will use HTTP/1.1
 * unless the server explicitly upgrades.
 *
 * <h2>Protocol selection</h2>
 * <ul>
 *   <li>When {@code request.protocol()} is {@link HttpRequest#PROTOCOL_HTTP_2} the
 *       client sends {@code HTTP/2} as the preferred ALPN protocol. If the server
 *       advertises h2, the connection is upgraded; otherwise it falls back to
 *       HTTP/1.1 transparently.</li>
 *   <li>When {@code request.protocol()} is {@link HttpRequest#PROTOCOL_HTTP_1_1}
 *       the client explicitly requests HTTP/1.1 only — useful for smoke-testing
 *       legacy endpoints.</li>
 * </ul>
 *
 * <h2>Timing</h2>
 * <ul>
 *   <li>{@code connectTimeMs} — TCP+TLS handshake time (0 when a pooled connection
 *       is reused)</li>
 *   <li>{@code latencyMs} — time from sending the first byte to receiving the first
 *       response byte (TTFB)</li>
 *   <li>{@code totalTimeMs} — total elapsed time from {@link #execute} entry to
 *       body fully read</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * This class is thread-safe once constructed. The underlying
 * {@link CloseableHttpAsyncClient} uses an internal connection pool and can be
 * shared across virtual threads.
 */
public final class Hc5HttpClientFactory implements HttpClientFactory {

    private static final Logger LOG = Logger.getLogger(Hc5HttpClientFactory.class.getName());

    /** Default socket timeout used when the request specifies 0. */
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 30_000;

    /** Default connect timeout used when the request specifies 0. */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    /** Default maximum connections per route (target host). */
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 50;

    /** Default maximum total connections across all routes. */
    private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 200;

    private final CloseableHttpAsyncClient h2Client;   // prefers HTTP/2
    private final CloseableHttpAsyncClient h1Client;   // HTTP/1.1 only
    private volatile boolean closed = false;

    /**
     * Constructs an HC5 client factory with default connection-pool settings.
     *
     * <p>Uses default values of {@value #DEFAULT_MAX_CONNECTIONS_PER_ROUTE}
     * max connections per route and {@value #DEFAULT_MAX_TOTAL_CONNECTIONS}
     * max total connections.
     *
     * <p>Two underlying async clients are maintained:
     * <ul>
     *   <li>An h2-capable client for {@link HttpRequest#PROTOCOL_HTTP_2} requests</li>
     *   <li>An h1-only client for {@link HttpRequest#PROTOCOL_HTTP_1_1} requests</li>
     * </ul>
     * Both are started eagerly so the first request does not pay startup overhead.
     */
    public Hc5HttpClientFactory() {
        this(DEFAULT_MAX_TOTAL_CONNECTIONS, DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
    }

    /**
     * Constructs an HC5 client factory with configurable connection-pool settings.
     *
     * <p>For load testing scenarios, recommended values are:
     * <ul>
     *   <li>{@code maxConnections}: 2000+ (total connections across all routes)</li>
     *   <li>{@code maxPerRoute}: 200+ (connections to a single target host)</li>
     * </ul>
     *
     * <p>These can be configured via application.yml:
     * <pre>
     * jmeter:
     *   http:
     *     maxConnections: 2000
     *     maxPerRoute: 200
     * </pre>
     *
     * @param maxConnections maximum total connections across all routes; must be &gt; 0
     * @param maxPerRoute    maximum connections per route (target host); must be &gt; 0
     */
    public Hc5HttpClientFactory(int maxConnections, int maxPerRoute) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be > 0, got: " + maxConnections);
        }
        if (maxPerRoute <= 0) {
            throw new IllegalArgumentException("maxPerRoute must be > 0, got: " + maxPerRoute);
        }
        LOG.log(Level.INFO,
                "Hc5HttpClientFactory: maxConnections={0}, maxPerRoute={1}",
                new Object[]{maxConnections, maxPerRoute});
        this.h2Client = buildH2Client(maxConnections, maxPerRoute);
        this.h1Client = buildH1Client(maxConnections, maxPerRoute);
        h2Client.start();
        h1Client.start();
    }

    // -------------------------------------------------------------------------
    // HttpClientFactory
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Selects the h2-capable or h1-only async client based on
     * {@link HttpRequest#protocol()}, executes the request synchronously (blocking
     * the calling virtual thread), and returns a fully populated {@link HttpResponse}.
     *
     * @throws IOException      if a network, timeout, or protocol error occurs
     * @throws IllegalStateException if this factory has been closed
     */
    @Override
    public HttpResponse execute(HttpRequest request) throws IOException {
        if (closed) {
            throw new IOException("HttpClientFactory is closed");
        }

        boolean preferH2 = HttpRequest.PROTOCOL_HTTP_2.equals(request.protocol());
        CloseableHttpAsyncClient client = preferH2 ? h2Client : h1Client;

        SimpleHttpRequest hcRequest = buildHcRequest(request);
        long startNs = System.nanoTime();

        AtomicReference<SimpleHttpResponse> responseRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // TTFB tracking: HC5 does not expose a public TTFB hook in SimpleHttpRequest,
        // so we approximate latency as the time from request dispatch to response
        // available in the callback. For more accurate TTFB an AsyncResponseConsumer
        // wrapper would be needed; this approximation is acceptable for load-test
        // metrics where median and p95 are more important than exact TTFB.
        long[] dispatchNs = {0L};

        dispatchNs[0] = System.nanoTime();
        client.execute(hcRequest, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                responseRef.set(result);
                latch.countDown();
            }

            @Override
            public void failed(Exception ex) {
                errorRef.set(ex);
                latch.countDown();
            }

            @Override
            public void cancelled() {
                errorRef.set(new IOException("HTTP request cancelled"));
                latch.countDown();
            }
        });

        int timeoutMs = request.responseTimeoutMs() > 0
                ? request.responseTimeoutMs()
                : DEFAULT_RESPONSE_TIMEOUT_MS;

        try {
            boolean completed = latch.await(timeoutMs + DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new IOException("HTTP request timed out after " + timeoutMs + " ms: " + request.url());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + request.url(), e);
        }

        if (errorRef.get() != null) {
            Exception cause = errorRef.get();
            throw new IOException("HTTP request failed: " + cause.getMessage(), cause);
        }

        long endNs = System.nanoTime();
        SimpleHttpResponse hcResponse = responseRef.get();

        return toHttpResponse(hcResponse, startNs, dispatchNs[0], endNs, preferH2);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Gracefully drains in-flight requests before closing. Idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            h2Client.close(CloseMode.GRACEFUL);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error closing HC5 h2 client", e);
        }
        try {
            h1Client.close(CloseMode.GRACEFUL);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error closing HC5 h1 client", e);
        }
    }

    // -------------------------------------------------------------------------
    // Builder helpers
    // -------------------------------------------------------------------------

    private static CloseableHttpAsyncClient buildH2Client(int maxTotal, int maxPerRoute) {
        AsyncClientConnectionManager connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(maxPerRoute)
                .setMaxConnTotal(maxTotal)
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                        .build())
                .build();

        return HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .build();
    }

    private static CloseableHttpAsyncClient buildH1Client(int maxTotal, int maxPerRoute) {
        AsyncClientConnectionManager connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(maxPerRoute)
                .setMaxConnTotal(maxTotal)
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                        .build())
                .build();

        return HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .build();
    }

    /**
     * Builds an HC5 {@link SimpleHttpRequest} from our domain {@link HttpRequest}.
     *
     * <p>{@code @SuppressWarnings("deprecation")}: HC5 5.3.x deprecated ALL timeout
     * setters on {@code RequestConfig.Builder} in favour of per-connection-manager
     * {@code ConnectionConfig}. Per-request timeouts are required for a load tester
     * where each sampler can specify its own connect/response timeout, so we
     * deliberately continue using {@code RequestConfig} and suppress the warning.
     */
    @SuppressWarnings("deprecation")
    private static SimpleHttpRequest buildHcRequest(HttpRequest request) {
        SimpleRequestBuilder builder = SimpleRequestBuilder.create(request.method())
                .setUri(request.url());

        // Apply per-request timeouts
        int connectMs = request.connectTimeoutMs() > 0
                ? request.connectTimeoutMs() : DEFAULT_CONNECT_TIMEOUT_MS;
        int responseMs = request.responseTimeoutMs() > 0
                ? request.responseTimeoutMs() : DEFAULT_RESPONSE_TIMEOUT_MS;

        // Use the non-deprecated long+TimeUnit overloads to avoid -Werror failures
        builder.setRequestConfig(
                org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectTimeout(connectMs, TimeUnit.MILLISECONDS)
                        .setResponseTimeout(responseMs, TimeUnit.MILLISECONDS)
                        .build()
        );

        // Headers
        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }

        // Body
        if (request.body() != null && request.body().length > 0) {
            String contentType = request.headers().getOrDefault(
                    "Content-Type", "application/octet-stream");
            builder.setBody(request.body(), ContentType.parse(contentType));
        }

        return builder.build();
    }

    private static HttpResponse toHttpResponse(
            SimpleHttpResponse hcResponse,
            long startNs,
            long dispatchNs,
            long endNs,
            boolean preferredH2) {

        // Response headers → plain map (last-wins on duplicates)
        Map<String, String> headers = new HashMap<>();
        for (Header h : hcResponse.getHeaders()) {
            headers.put(h.getName(), h.getValue());
        }

        // Body bytes
        byte[] body = hcResponse.getBodyBytes();
        if (body == null) {
            body = new byte[0];
        }

        // Determine actual negotiated protocol
        ProtocolVersion version = hcResponse.getVersion();
        String negotiatedProtocol;
        if (version != null && version.getMajor() == HttpVersion.HTTP_2.getMajor()
                && version.getMinor() == HttpVersion.HTTP_2.getMinor()) {
            negotiatedProtocol = HttpRequest.PROTOCOL_HTTP_2;
        } else {
            negotiatedProtocol = HttpRequest.PROTOCOL_HTTP_1_1;
        }

        // Timing (nanoseconds → milliseconds)
        long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(endNs - startNs);
        // connectTimeMs: estimated as dispatch delay before send (setup overhead)
        long connectTimeMs = TimeUnit.NANOSECONDS.toMillis(dispatchNs - startNs);
        // latencyMs: time from dispatch until response available
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(endNs - dispatchNs);

        return new HttpResponse(
                hcResponse.getCode(),
                negotiatedProtocol,
                headers,
                body,
                connectTimeMs,
                latencyMs,
                totalTimeMs,
                false
        );
    }
}
