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
package com.b3meter.engine.adapter.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link Hc5HttpClientFactory} against a local WireMock server.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>HTTP GET — 200 response, body, headers</li>
 *   <li>HTTP POST — request body forwarded, 201 response</li>
 *   <li>Response time recording — all three timing dimensions are non-negative</li>
 *   <li>Timeout handling — {@link IOException} thrown when server delays beyond timeout</li>
 *   <li>Non-2xx responses — {@link HttpResponse} returned (not thrown)</li>
 *   <li>Factory close — subsequent calls throw {@link IOException}</li>
 * </ul>
 *
 * <p>These tests use plain HTTP (no TLS) so the protocol negotiated will always be
 * HTTP/1.1. Protocol negotiation (HTTP/2 via ALPN) is exercised separately in
 * {@link HttpProtocolNegotiationTest}.
 */
class Hc5HttpClientFactoryTest {

    private WireMockServer wireMock;
    private Hc5HttpClientFactory factory;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
        factory = new Hc5HttpClientFactory();
    }

    @AfterEach
    void tearDown() {
        factory.close();
        wireMock.stop();
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    @Test
    void getRequest_returns200WithBody() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("hello world")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/hello"));

        assertEquals(200, response.statusCode());
        assertEquals("hello world", response.bodyAsString());
        assertTrue(response.isSuccess());
    }

    @Test
    void getRequest_responseHeadersArePropagated() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/with-header"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Header", "custom-value")
                        .withBody("")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/with-header"));

        assertEquals("custom-value", response.headers().get("X-Custom-Header"));
    }

    @Test
    void getRequest_customRequestHeadersAreSent() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/auth"))
                .withHeader("Authorization", equalTo("Bearer token123"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest request = new HttpRequest(
                "GET", baseUrl + "/auth",
                Map.of("Authorization", "Bearer token123"),
                null, 0, 0, HttpRequest.PROTOCOL_HTTP_1_1
        );

        HttpResponse response = factory.execute(request);
        assertEquals(200, response.statusCode());
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    @Test
    void postRequest_sendsBodyAndReturns201() throws IOException {
        String requestBody = "{\"name\":\"test\"}";
        wireMock.stubFor(post(urlEqualTo("/items"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));

        HttpRequest request = new HttpRequest(
                "POST", baseUrl + "/items",
                Map.of("Content-Type", "application/json"),
                requestBody.getBytes(StandardCharsets.UTF_8),
                0, 0, HttpRequest.PROTOCOL_HTTP_1_1
        );

        HttpResponse response = factory.execute(request);

        assertEquals(201, response.statusCode());
        assertTrue(response.bodyAsString().contains("\"id\":1"));
    }

    // -------------------------------------------------------------------------
    // Non-2xx responses
    // -------------------------------------------------------------------------

    @Test
    void notFoundResponse_returnedAsHttpResponse_notThrown() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/missing"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/missing"));

        assertEquals(404, response.statusCode());
        assertFalse(response.isSuccess());
        assertEquals("not found", response.bodyAsString());
    }

    @Test
    void serverError_returnedAsHttpResponse_notThrown() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/error"))
                .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/error"));

        assertEquals(503, response.statusCode());
        assertFalse(response.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Response time recording
    // -------------------------------------------------------------------------

    @Test
    void responseTimings_areNonNegative() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/timing"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/timing"));

        assertAll("timing dimensions must be non-negative",
                () -> assertTrue(response.connectTimeMs() >= 0,
                        "connectTimeMs should be >= 0, was: " + response.connectTimeMs()),
                () -> assertTrue(response.latencyMs() >= 0,
                        "latencyMs should be >= 0, was: " + response.latencyMs()),
                () -> assertTrue(response.totalTimeMs() >= 0,
                        "totalTimeMs should be >= 0, was: " + response.totalTimeMs())
        );
    }

    @Test
    void totalTimeMsShouldBeAtLeastLatencyMs() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/timing2"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/timing2"));

        assertTrue(response.totalTimeMs() >= response.latencyMs(),
                "totalTimeMs (" + response.totalTimeMs() + ") must be >= latencyMs ("
                        + response.latencyMs() + ")");
    }

    // -------------------------------------------------------------------------
    // Timeout handling
    // -------------------------------------------------------------------------

    @Test
    void responseTimeout_throwsIOException() {
        wireMock.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(3_000)   // 3 seconds delay
                        .withBody("late response")));

        HttpRequest request = new HttpRequest(
                "GET", baseUrl + "/slow",
                Map.of(), null,
                10_000,  // connect timeout — generous
                500,     // response timeout — 500 ms, server delays 3 s → must timeout
                HttpRequest.PROTOCOL_HTTP_1_1
        );

        assertThrows(IOException.class, () -> factory.execute(request),
                "Expected IOException due to response timeout");
    }

    // -------------------------------------------------------------------------
    // Factory lifecycle
    // -------------------------------------------------------------------------

    @Test
    void afterClose_executeThrowsIOException() {
        factory.close();

        assertThrows(IOException.class,
                () -> factory.execute(HttpRequest.get(baseUrl + "/any")),
                "Expected IOException after factory is closed");
    }

    @Test
    void close_isIdempotent() {
        // calling close() twice must not throw
        assertDoesNotThrow(() -> {
            factory.close();
            factory.close();
        });
    }

    // -------------------------------------------------------------------------
    // Empty body
    // -------------------------------------------------------------------------

    @Test
    void emptyResponseBody_returnedAsEmptyByteArray() throws IOException {
        wireMock.stubFor(get(urlEqualTo("/empty"))
                .willReturn(aResponse().withStatus(204)));

        HttpResponse response = factory.execute(HttpRequest.get(baseUrl + "/empty"));

        assertEquals(204, response.statusCode());
        assertNotNull(response.body(), "body must never be null");
    }
}
