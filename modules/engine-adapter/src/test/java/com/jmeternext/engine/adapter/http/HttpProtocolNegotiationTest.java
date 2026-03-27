package com.jmeternext.engine.adapter.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jmeternext.engine.service.http.HttpRequest;
import com.jmeternext.engine.service.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol negotiation tests for {@link Hc5HttpClientFactory} and
 * {@link Hc4HttpClientFactory}.
 *
 * <h2>HTTP/2 availability</h2>
 * WireMock's embedded Jetty 12 server does not advertise h2 over plain HTTP (h2c
 * requires a prior-knowledge upgrade or server push, which WireMock does not enable
 * by default). Therefore:
 * <ul>
 *   <li>When {@code PROTOCOL_HTTP_2} is requested against a plain-HTTP WireMock
 *       server, ALPN cannot negotiate h2 and the client falls back to HTTP/1.1.</li>
 *   <li>The test verifies that the fallback is transparent (200 received) and that
 *       the negotiated protocol is reported correctly.</li>
 * </ul>
 *
 * <h2>HC4 behaviour</h2>
 * {@link Hc4HttpClientFactory} never negotiates HTTP/2 — it always reports
 * {@code "HTTP/1.1"} regardless of the requested protocol. This is the expected
 * backward-compatibility contract.
 *
 * <h2>Explicit HTTP/1.1 force</h2>
 * When {@link HttpRequest#PROTOCOL_HTTP_1_1} is requested the response must report
 * {@code "HTTP/1.1"} even when the server would support h2.
 */
class HttpProtocolNegotiationTest {

    private WireMockServer wireMock;
    private Hc5HttpClientFactory hc5Factory;
    private Hc4HttpClientFactory hc4Factory;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();

        hc5Factory = new Hc5HttpClientFactory();
        hc4Factory = new Hc4HttpClientFactory();

        wireMock.stubFor(get(anyUrl())
                .willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void tearDown() {
        hc5Factory.close();
        hc4Factory.close();
        wireMock.stop();
    }

    // -------------------------------------------------------------------------
    // HC5 — HTTP/1.1 explicit
    // -------------------------------------------------------------------------

    @Test
    void hc5_explicitHttp11Request_reportsHttp11() throws IOException {
        HttpResponse response = hc5Factory.execute(HttpRequest.get(baseUrl + "/proto"));

        assertEquals(200, response.statusCode());
        assertEquals(HttpRequest.PROTOCOL_HTTP_1_1, response.protocol(),
                "Explicit HTTP/1.1 request must report HTTP/1.1 protocol");
        assertFalse(response.isHttp2(),
                "isHttp2() must be false for HTTP/1.1 response");
    }

    // -------------------------------------------------------------------------
    // HC5 — HTTP/2 preference with fallback
    // -------------------------------------------------------------------------

    @Test
    void hc5_http2RequestAgainstHttp11Server_fallsBackToHttp11() throws IOException {
        // WireMock plain-HTTP server does not support ALPN h2 negotiation.
        // The HC5 client must fall back transparently to HTTP/1.1.
        HttpRequest request = HttpRequest.getH2(baseUrl + "/proto");
        HttpResponse response = hc5Factory.execute(request);

        assertEquals(200, response.statusCode(),
                "Response must be 200 even after h2 → h1.1 fallback");
        // After falling back the negotiated protocol must be HTTP/1.1
        assertEquals(HttpRequest.PROTOCOL_HTTP_1_1, response.protocol(),
                "Protocol must be HTTP/1.1 after fallback from HTTP/2 preference");
    }

    @Test
    void hc5_http2Request_isHttp2ReturnsFalseAfterFallback() throws IOException {
        HttpResponse response = hc5Factory.execute(HttpRequest.getH2(baseUrl + "/proto"));

        assertFalse(response.isHttp2(),
                "isHttp2() must be false after falling back from HTTP/2 to HTTP/1.1");
    }

    // -------------------------------------------------------------------------
    // HC4 — always HTTP/1.1
    // -------------------------------------------------------------------------

    @Test
    void hc4_alwaysReportsHttp11_forHttp11Request() throws IOException {
        HttpResponse response = hc4Factory.execute(HttpRequest.get(baseUrl + "/proto"));

        assertEquals(200, response.statusCode());
        assertEquals(HttpRequest.PROTOCOL_HTTP_1_1, response.protocol(),
                "HC4 client must always report HTTP/1.1");
    }

    @Test
    void hc4_alwaysReportsHttp11_evenWhenHttp2IsRequested() throws IOException {
        // HC4-style client ignores the protocol preference — always uses HTTP/1.1
        HttpRequest request = HttpRequest.getH2(baseUrl + "/proto");
        HttpResponse response = hc4Factory.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals(HttpRequest.PROTOCOL_HTTP_1_1, response.protocol(),
                "HC4 client must report HTTP/1.1 even when HTTP/2 was requested");
        assertFalse(response.isHttp2());
    }

    // -------------------------------------------------------------------------
    // Timing dimensions non-negative for both implementations
    // -------------------------------------------------------------------------

    @Test
    void hc5_timingDimensionsNonNegative() throws IOException {
        HttpResponse response = hc5Factory.execute(HttpRequest.get(baseUrl + "/timing"));

        assertAll("HC5 timing dimensions",
                () -> assertTrue(response.connectTimeMs() >= 0),
                () -> assertTrue(response.latencyMs() >= 0),
                () -> assertTrue(response.totalTimeMs() >= 0)
        );
    }

    @Test
    void hc4_timingDimensionsNonNegative() throws IOException {
        HttpResponse response = hc4Factory.execute(HttpRequest.get(baseUrl + "/timing"));

        assertAll("HC4 timing dimensions",
                () -> assertTrue(response.connectTimeMs() >= 0),
                () -> assertTrue(response.latencyMs() >= 0),
                () -> assertTrue(response.totalTimeMs() >= 0)
        );
    }

    // -------------------------------------------------------------------------
    // Both factories satisfy the HttpClientFactory contract
    // -------------------------------------------------------------------------

    @Test
    void hc5_implementsHttpClientFactory() {
        assertInstanceOf(
                com.jmeternext.engine.service.http.HttpClientFactory.class,
                hc5Factory,
                "Hc5HttpClientFactory must implement HttpClientFactory"
        );
    }

    @Test
    void hc4_implementsHttpClientFactory() {
        assertInstanceOf(
                com.jmeternext.engine.service.http.HttpClientFactory.class,
                hc4Factory,
                "Hc4HttpClientFactory must implement HttpClientFactory"
        );
    }
}
