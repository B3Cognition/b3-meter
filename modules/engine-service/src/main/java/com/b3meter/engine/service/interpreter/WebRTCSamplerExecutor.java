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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code WebRTCSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code WebRTCSampler.signalingUrl} — HTTPS URL for SDP offer/answer exchange</li>
 *   <li>{@code WebRTCSampler.offerSdp} — SDP offer string (or "generate" for a minimal offer)</li>
 *   <li>{@code WebRTCSampler.connectTimeout} — connect timeout in ms (default 10000)</li>
 * </ul>
 *
 * <p>This is signaling-only — actual WebRTC media requires native libraries (libnice,
 * libsrtp2) which are out of scope for pure JDK. The executor POSTs an SDP offer to the
 * signaling URL and reads the SDP answer, measuring the signaling round-trip time.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class WebRTCSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(WebRTCSamplerExecutor.class.getName());

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;

    /** Minimal SDP offer used when offerSdp is set to "generate". */
    private static final String MINIMAL_SDP_OFFER =
            "v=0\r\n"
            + "o=- 0 0 IN IP4 127.0.0.1\r\n"
            + "s=-\r\n"
            + "t=0 0\r\n"
            + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n"
            + "c=IN IP4 0.0.0.0\r\n"
            + "a=ice-ufrag:jmtr\r\n"
            + "a=ice-pwd:b3meterwebrtcloadtest\r\n"
            + "a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00"
            + ":00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00\r\n"
            + "a=setup:actpass\r\n"
            + "a=mid:0\r\n"
            + "a=sctp-port:5000\r\n";

    private WebRTCSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the WebRTC signaling operation described by {@code node}.
     *
     * @param node      the WebRTCSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String signalingUrl = resolve(node.getStringProp("WebRTCSampler.signalingUrl", ""), variables);
        String offerSdp = resolve(node.getStringProp("WebRTCSampler.offerSdp", "generate"), variables);
        int connectTimeout = node.getIntProp("WebRTCSampler.connectTimeout", DEFAULT_CONNECT_TIMEOUT_MS);

        if (signalingUrl.isBlank()) {
            result.setFailureMessage("WebRTCSampler.signalingUrl is empty");
            return;
        }

        // Resolve offer SDP
        String sdpBody = "generate".equalsIgnoreCase(offerSdp.trim()) ? MINIMAL_SDP_OFFER : offerSdp;

        LOG.log(Level.FINE, "WebRTCSamplerExecutor: posting SDP offer to {0}", signalingUrl);

        long start = System.currentTimeMillis();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeout))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(signalingUrl))
                    .timeout(Duration.ofMillis(connectTimeout))
                    .header("Content-Type", "application/sdp")
                    .POST(HttpRequest.BodyPublishers.ofString(sdpBody, StandardCharsets.UTF_8))
                    .build();

            long connectTime = System.currentTimeMillis() - start;

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            long total = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);
            result.setLatencyMs(total - connectTime);
            result.setTotalTimeMs(total);
            result.setStatusCode(response.statusCode());
            result.setResponseBody(response.body());

            if (response.statusCode() >= 400) {
                result.setFailureMessage("WebRTC signaling HTTP " + response.statusCode()
                        + " on " + signalingUrl);
            }

            LOG.log(Level.FINE, "WebRTCSamplerExecutor: signaling RTT {0}ms, status {1}",
                    new Object[]{total, response.statusCode()});

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("WebRTC signaling error: " + e.getMessage());
            LOG.log(Level.WARNING, "WebRTCSamplerExecutor: error for " + signalingUrl, e);
        } catch (InterruptedException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("WebRTC signaling interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the minimal SDP offer used when offerSdp is "generate".
     * Package-visible for testing.
     */
    static String getMinimalSdpOffer() {
        return MINIMAL_SDP_OFFER;
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
