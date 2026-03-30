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

import com.b3meter.engine.service.plan.PlanNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WebRTCSamplerExecutor}.
 *
 * <p>Uses an embedded {@link HttpServer} to simulate a WebRTC signaling server.
 * No external libraries are required (Constitution Principle I).
 */
class WebRTCSamplerExecutorTest {

    private HttpServer server;
    private int serverPort;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("webrtc-test");
        assertThrows(NullPointerException.class,
                () -> WebRTCSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = webrtcNode("http://localhost/signal", "generate");
        assertThrows(NullPointerException.class,
                () -> WebRTCSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = webrtcNode("http://localhost/signal", "generate");
        SampleResult result = new SampleResult("webrtc-test");
        assertThrows(NullPointerException.class,
                () -> WebRTCSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty URL validation
    // =========================================================================

    @Test
    void execute_failsOnEmptySignalingUrl() {
        PlanNode node = webrtcNode("", "generate");
        SampleResult result = new SampleResult("webrtc-test");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("signalingUrl is empty"));
    }

    // =========================================================================
    // Successful signaling exchange
    // =========================================================================

    @Test
    void execute_postsOfferAndReceivesAnswer() {
        String sdpAnswer = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n"
                + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n"
                + "a=setup:active\r\n";

        server.createContext("/signal", exchange -> {
            // Verify request
            assertEquals("POST", exchange.getRequestMethod());
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            assertTrue(contentType.contains("application/sdp"), "Content-Type should be application/sdp");

            // Read offer body
            byte[] offerBytes = exchange.getRequestBody().readAllBytes();
            String offer = new String(offerBytes, StandardCharsets.UTF_8);
            assertTrue(offer.contains("v=0"), "Offer should contain SDP version");

            // Send answer
            byte[] answerBytes = sdpAnswer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answerBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answerBytes);
            }
        });
        server.start();

        PlanNode node = webrtcNode("http://127.0.0.1:" + serverPort + "/signal", "generate");
        SampleResult result = new SampleResult("webrtc-signaling");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), "Expected success but: " + result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("v=0"), "Response should contain SDP answer");
        assertTrue(result.getResponseBody().contains("setup:active"));
        assertTrue(result.getConnectTimeMs() >= 0);
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_usesCustomSdpOffer() {
        String customOffer = "v=0\r\no=- 42 42 IN IP4 10.0.0.1\r\ns=custom\r\nt=0 0\r\n";

        server.createContext("/signal", exchange -> {
            byte[] offerBytes = exchange.getRequestBody().readAllBytes();
            String offer = new String(offerBytes, StandardCharsets.UTF_8);
            assertTrue(offer.contains("s=custom"), "Should use custom SDP offer");

            byte[] answer = "v=0\r\na=ok\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();

        PlanNode node = webrtcNode("http://127.0.0.1:" + serverPort + "/signal", customOffer);
        SampleResult result = new SampleResult("webrtc-custom");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void execute_generatesMinimalSdpWhenSetToGenerate() {
        String minimalSdp = WebRTCSamplerExecutor.getMinimalSdpOffer();
        assertNotNull(minimalSdp);
        assertTrue(minimalSdp.contains("v=0"));
        assertTrue(minimalSdp.contains("ice-ufrag"));
        assertTrue(minimalSdp.contains("fingerprint"));
    }

    // =========================================================================
    // HTTP error handling
    // =========================================================================

    @Test
    void execute_failsOnHttp4xx() {
        server.createContext("/signal", exchange -> {
            exchange.sendResponseHeaders(403, -1);
        });
        server.start();

        PlanNode node = webrtcNode("http://127.0.0.1:" + serverPort + "/signal", "generate");
        SampleResult result = new SampleResult("webrtc-403");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(403, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("403"));
    }

    @Test
    void execute_handlesConnectionRefused() {
        // Don't start the server
        PlanNode node = webrtcNode("http://127.0.0.1:" + serverPort + "/signal", "generate");
        SampleResult result = new SampleResult("webrtc-refused");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariablesInUrl() {
        server.createContext("/signal", exchange -> {
            byte[] answer = "v=0\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();

        PlanNode node = PlanNode.builder("WebRTCSampler", "webrtc-var")
                .property("WebRTCSampler.signalingUrl", "http://127.0.0.1:${rtcPort}/signal")
                .property("WebRTCSampler.offerSdp", "generate")
                .build();
        SampleResult result = new SampleResult("webrtc-var");
        Map<String, String> vars = new HashMap<>();
        vars.put("rtcPort", String.valueOf(serverPort));

        WebRTCSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), result.getFailureMessage());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void execute_resolvesVariablesInOfferSdp() {
        server.createContext("/signal", exchange -> {
            byte[] offerBytes = exchange.getRequestBody().readAllBytes();
            String offer = new String(offerBytes, StandardCharsets.UTF_8);
            assertTrue(offer.contains("s=my-session"), "Should resolve variable in SDP");

            byte[] answer = "v=0\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();

        PlanNode node = PlanNode.builder("WebRTCSampler", "webrtc-sdpvar")
                .property("WebRTCSampler.signalingUrl", "http://127.0.0.1:" + serverPort + "/signal")
                .property("WebRTCSampler.offerSdp", "v=0\r\ns=${sessionName}\r\nt=0 0\r\n")
                .build();
        SampleResult result = new SampleResult("webrtc-sdpvar");
        Map<String, String> vars = new HashMap<>();
        vars.put("sessionName", "my-session");

        WebRTCSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), result.getFailureMessage());
    }

    // =========================================================================
    // Timing recorded
    // =========================================================================

    @Test
    void execute_recordsTiming() {
        server.createContext("/signal", exchange -> {
            byte[] answer = "v=0\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        server.start();

        PlanNode node = webrtcNode("http://127.0.0.1:" + serverPort + "/signal", "generate");
        SampleResult result = new SampleResult("webrtc-timing");

        WebRTCSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.getConnectTimeMs() >= 0, "Connect time should be recorded");
        assertTrue(result.getLatencyMs() >= 0, "Latency should be recorded");
        assertTrue(result.getTotalTimeMs() >= 0, "Total time should be recorded");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode webrtcNode(String signalingUrl, String offerSdp) {
        return PlanNode.builder("WebRTCSampler", "webrtc-test")
                .property("WebRTCSampler.signalingUrl", signalingUrl)
                .property("WebRTCSampler.offerSdp", offerSdp)
                .property("WebRTCSampler.connectTimeout", 10000)
                .build();
    }
}
