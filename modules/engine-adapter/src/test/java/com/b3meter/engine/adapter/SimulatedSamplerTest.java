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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.b3meter.engine.adapter.http.Hc5HttpClientFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimulatedSampler}.
 *
 * <p>Uses WireMock to provide a real HTTP server so timing is recorded against
 * actual network I/O (loopback), not mocked responses.
 */
class SimulatedSamplerTest {

    private static WireMockServer wireMock;
    private static Hc5HttpClientFactory httpClientFactory;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        httpClientFactory = new Hc5HttpClientFactory();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
        if (httpClientFactory != null) httpClientFactory.close();
    }

    // -------------------------------------------------------------------------
    // Successful 200 response
    // -------------------------------------------------------------------------

    @Test
    void executeReturnsSuccessOnHttp200() {
        wireMock.stubFor(get(urlEqualTo("/ping"))
                .willReturn(aResponse().withStatus(200).withBody("pong")));

        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:" + wireMock.port() + "/ping",
                "PingTest"
        );

        SimulatedSampler.SampleResult result = sampler.execute();

        assertTrue(result.success(), "Expected success for HTTP 200");
        assertEquals(200, result.statusCode());
        assertEquals("PingTest", result.label());
        assertTrue(result.totalTimeMs() >= 0, "totalTimeMs must be non-negative");
        assertTrue(result.responseBytes() > 0, "Expected non-empty response body");
    }

    @Test
    void executeReturnsFailureOnHttp500() {
        wireMock.stubFor(get(urlEqualTo("/error"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:" + wireMock.port() + "/error",
                "ErrorTest"
        );

        SimulatedSampler.SampleResult result = sampler.execute();

        assertFalse(result.success(), "Expected failure for HTTP 500");
        assertEquals(500, result.statusCode());
        assertEquals("ErrorTest", result.label());
    }

    @Test
    void executeReturnsFailureOnConnectionRefused() {
        // Port 1 should be unreachable / connection refused
        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:1/unreachable",
                "UnreachableTest"
        );

        SimulatedSampler.SampleResult result = sampler.execute();

        assertFalse(result.success(), "Expected failure when connection is refused");
        assertEquals(0, result.statusCode(), "Status code should be 0 on connection failure");
    }

    @Test
    void executeRecordsTiming() {
        wireMock.stubFor(get(urlEqualTo("/timed"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(50).withBody("ok")));

        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:" + wireMock.port() + "/timed",
                "TimedTest"
        );

        SimulatedSampler.SampleResult result = sampler.execute();

        assertTrue(result.success());
        assertTrue(result.totalTimeMs() >= 40,
                "totalTimeMs (" + result.totalTimeMs() + ") should reflect the 50ms fixed delay");
    }

    @Test
    void executeSetsLabelFromConstructor() {
        wireMock.stubFor(get(urlEqualTo("/label"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:" + wireMock.port() + "/label",
                "MyCustomLabel"
        );

        SimulatedSampler.SampleResult result = sampler.execute();
        assertEquals("MyCustomLabel", result.label());
    }

    @Test
    void defaultLabelIsUsedWhenNotSpecified() {
        wireMock.stubFor(get(urlEqualTo("/default-label"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        SimulatedSampler sampler = new SimulatedSampler(
                httpClientFactory,
                "http://localhost:" + wireMock.port() + "/default-label"
        );

        assertEquals(SimulatedSampler.DEFAULT_LABEL, sampler.getLabel());
        SimulatedSampler.SampleResult result = sampler.execute();
        assertEquals(SimulatedSampler.DEFAULT_LABEL, result.label());
    }

    @Test
    void constructorThrowsOnNullHttpClientFactory() {
        assertThrows(NullPointerException.class,
                () -> new SimulatedSampler(null, "http://localhost/test", "label"));
    }

    @Test
    void constructorThrowsOnNullUrl() {
        assertThrows(NullPointerException.class,
                () -> new SimulatedSampler(httpClientFactory, null, "label"));
    }

    @Test
    void constructorThrowsOnNullLabel() {
        assertThrows(NullPointerException.class,
                () -> new SimulatedSampler(httpClientFactory, "http://localhost/test", null));
    }
}
