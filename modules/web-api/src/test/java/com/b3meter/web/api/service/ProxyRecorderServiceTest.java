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
package com.b3meter.web.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProxyRecorderService}.
 *
 * <p>Pure unit tests — no Spring context required. All state is in-memory.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Start / stop / isRecording lifecycle</li>
 *   <li>Default vs. custom configuration</li>
 *   <li>Capture accepted when recording, rejected when stopped</li>
 *   <li>Exclusion pattern filtering</li>
 *   <li>Include pattern filtering</li>
 *   <li>Conversion to HTTPSampler test-plan nodes</li>
 *   <li>Header Manager child node generation</li>
 *   <li>Invalid argument guard-rails</li>
 * </ul>
 */
class ProxyRecorderServiceTest {

    private static final String PLAN_ID = "plan-test-001";

    private ProxyRecorderService service;

    @BeforeEach
    void setUp() {
        service = new ProxyRecorderService();
    }

    // -------------------------------------------------------------------------
    // Start / stop lifecycle
    // -------------------------------------------------------------------------

    @Test
    void isRecording_falseBeforeStart() {
        assertFalse(service.isRecording(PLAN_ID));
    }

    @Test
    void isRecording_trueAfterStart() {
        service.startRecording(PLAN_ID, null);
        assertTrue(service.isRecording(PLAN_ID));
    }

    @Test
    void isRecording_falseAfterStop() {
        service.startRecording(PLAN_ID, null);
        service.stopRecording(PLAN_ID);
        assertFalse(service.isRecording(PLAN_ID));
    }

    @Test
    void stopRecording_isIdempotentWhenNotStarted() {
        // Should not throw
        assertDoesNotThrow(() -> service.stopRecording(PLAN_ID));
    }

    @Test
    void startRecording_replacesExistingSession() {
        service.startRecording(PLAN_ID, null);
        CapturedRequest req = makeRequest("GET", "https://example.com/api/data");
        service.capture(PLAN_ID, req);
        assertEquals(1, service.getCaptured(PLAN_ID).size());

        // Restart — captured list should be cleared
        service.startRecording(PLAN_ID, null);
        assertEquals(0, service.getCaptured(PLAN_ID).size());
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    @Test
    void getConfig_returnsDefaultWhenNotRecording() {
        ProxyRecorderConfig cfg = service.getConfig(PLAN_ID);
        assertEquals(ProxyRecorderConfig.DEFAULT_PORT, cfg.port());
        assertNull(cfg.targetBaseUrl());
        assertTrue(cfg.captureHeaders());
        assertTrue(cfg.captureBody());
    }

    @Test
    void getConfig_returnsCustomConfig() {
        ProxyRecorderConfig custom = new ProxyRecorderConfig(
                9090, "https://backend.internal", List.of(), List.of(), false, false);
        service.startRecording(PLAN_ID, custom);

        ProxyRecorderConfig cfg = service.getConfig(PLAN_ID);
        assertEquals(9090, cfg.port());
        assertEquals("https://backend.internal", cfg.targetBaseUrl());
        assertFalse(cfg.captureHeaders());
        assertFalse(cfg.captureBody());
    }

    @Test
    void startRecording_withNullConfig_usesDefaults() {
        service.startRecording(PLAN_ID, null);
        assertEquals(ProxyRecorderConfig.DEFAULT_PORT, service.getConfig(PLAN_ID).port());
    }

    // -------------------------------------------------------------------------
    // Capture acceptance / rejection
    // -------------------------------------------------------------------------

    @Test
    void capture_returnsFalseWhenNotRecording() {
        CapturedRequest req = makeRequest("GET", "https://example.com/api");
        assertFalse(service.capture(PLAN_ID, req));
    }

    @Test
    void capture_returnsTrueAndStoresWhenRecording() {
        service.startRecording(PLAN_ID, null);
        CapturedRequest req = makeRequest("GET", "https://example.com/api/data");
        assertTrue(service.capture(PLAN_ID, req));
        assertEquals(1, service.getCaptured(PLAN_ID).size());
    }

    @Test
    void capture_returnsFalseAfterStop() {
        service.startRecording(PLAN_ID, null);
        service.stopRecording(PLAN_ID);
        CapturedRequest req = makeRequest("GET", "https://example.com/api");
        assertFalse(service.capture(PLAN_ID, req));
    }

    // -------------------------------------------------------------------------
    // Exclusion pattern filtering
    // -------------------------------------------------------------------------

    @Test
    void capture_filtersOutImagesByDefault() {
        service.startRecording(PLAN_ID, null);

        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/logo.png")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/sprite.gif")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/style.css")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/app.js")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/font.woff2")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://cdn.example.com/favicon.ico")));
        assertEquals(0, service.getCaptured(PLAN_ID).size());
    }

    @Test
    void capture_acceptsApiUrlByDefault() {
        service.startRecording(PLAN_ID, null);
        assertTrue(service.capture(PLAN_ID, makeRequest("GET", "https://example.com/api/users")));
    }

    @Test
    void capture_respectsCustomExcludePattern() {
        ProxyRecorderConfig cfg = new ProxyRecorderConfig(
                8888, null, List.of(), List.of(".*\\.json(\\?.*)?$"), true, true);
        service.startRecording(PLAN_ID, cfg);

        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://api.example.com/data.json")));
        assertTrue(service.capture(PLAN_ID, makeRequest("GET", "https://api.example.com/data")));
    }

    // -------------------------------------------------------------------------
    // Include pattern filtering
    // -------------------------------------------------------------------------

    @Test
    void capture_respectsIncludePatterns() {
        ProxyRecorderConfig cfg = new ProxyRecorderConfig(
                8888, null, List.of("https://api\\.example\\.com/.*"), List.of(), true, true);
        service.startRecording(PLAN_ID, cfg);

        assertTrue(service.capture(PLAN_ID, makeRequest("GET", "https://api.example.com/users")));
        assertFalse(service.capture(PLAN_ID, makeRequest("GET", "https://other.example.com/users")));
    }

    // -------------------------------------------------------------------------
    // getCaptured
    // -------------------------------------------------------------------------

    @Test
    void getCaptured_returnsEmptyListWhenNoSession() {
        assertEquals(0, service.getCaptured(PLAN_ID).size());
    }

    @Test
    void getCaptured_returnsAllStoredRequests() {
        service.startRecording(PLAN_ID, null);
        service.capture(PLAN_ID, makeRequest("GET", "https://example.com/api/a"));
        service.capture(PLAN_ID, makeRequest("POST", "https://example.com/api/b"));
        assertEquals(2, service.getCaptured(PLAN_ID).size());
    }

    // -------------------------------------------------------------------------
    // applyToTestPlan — node structure
    // -------------------------------------------------------------------------

    @Test
    void applyToTestPlan_returnsEmptyListWhenNothingCaptured() {
        service.startRecording(PLAN_ID, null);
        assertTrue(service.applyToTestPlan(PLAN_ID).isEmpty());
    }

    @Test
    void applyToTestPlan_createsHttpSamplerNode() {
        service.startRecording(PLAN_ID, null);
        CapturedRequest req = ProxyRecorderService.newCapturedRequest(
                "GET", "https://example.com/api/items", Map.of(), new byte[0], 200, 42L);
        service.capture(PLAN_ID, req);

        List<Map<String, Object>> nodes = service.applyToTestPlan(PLAN_ID);
        assertEquals(1, nodes.size());

        Map<String, Object> node = nodes.get(0);
        assertEquals("HTTPSampler", node.get("type"));
        assertEquals(true, node.get("enabled"));
        assertNotNull(node.get("id"));
        assertNotNull(node.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) node.get("properties");
        assertEquals("GET", props.get("method"));
        assertEquals("https", props.get("protocol"));
        assertEquals("example.com", props.get("server"));
        assertEquals("/api/items", props.get("path"));
    }

    @Test
    void applyToTestPlan_addsHeaderManagerChildWhenHeadersPresent() {
        service.startRecording(PLAN_ID, null);
        CapturedRequest req = ProxyRecorderService.newCapturedRequest(
                "POST", "https://example.com/api/submit",
                Map.of("Content-Type", "application/json", "Authorization", "Bearer tok"),
                "{\"x\":1}".getBytes(),
                201, 100L);
        service.capture(PLAN_ID, req);

        List<Map<String, Object>> nodes = service.applyToTestPlan(PLAN_ID);
        assertEquals(1, nodes.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) nodes.get(0).get("children");
        assertEquals(1, children.size());
        assertEquals("HeaderManager", children.get(0).get("type"));
    }

    @Test
    void applyToTestPlan_noHeaderManagerWhenHeadersEmpty() {
        service.startRecording(PLAN_ID, null);
        CapturedRequest req = ProxyRecorderService.newCapturedRequest(
                "GET", "https://example.com/api/ping", Map.of(), new byte[0], 200, 10L);
        service.capture(PLAN_ID, req);

        List<Map<String, Object>> nodes = service.applyToTestPlan(PLAN_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) nodes.get(0).get("children");
        assertTrue(children.isEmpty());
    }

    @Test
    void applyToTestPlan_capturesBodyAsString() {
        service.startRecording(PLAN_ID, null);
        byte[] bodyBytes = "{\"name\":\"Alice\"}".getBytes();
        CapturedRequest req = ProxyRecorderService.newCapturedRequest(
                "POST", "https://example.com/api/users", Map.of(), bodyBytes, 201, 55L);
        service.capture(PLAN_ID, req);

        List<Map<String, Object>> nodes = service.applyToTestPlan(PLAN_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) nodes.get(0).get("properties");
        assertEquals("{\"name\":\"Alice\"}", props.get("body"));
    }

    // -------------------------------------------------------------------------
    // Multiple plans
    // -------------------------------------------------------------------------

    @Test
    void multiplePlans_areIsolated() {
        String planA = "plan-a";
        String planB = "plan-b";
        service.startRecording(planA, null);
        service.startRecording(planB, null);

        service.capture(planA, makeRequest("GET", "https://example.com/api/a"));
        service.capture(planA, makeRequest("GET", "https://example.com/api/b"));
        service.capture(planB, makeRequest("GET", "https://example.com/api/c"));

        assertEquals(2, service.getCaptured(planA).size());
        assertEquals(1, service.getCaptured(planB).size());
    }

    // -------------------------------------------------------------------------
    // Guard-rails
    // -------------------------------------------------------------------------

    @Test
    void startRecording_throwsOnBlankPlanId() {
        assertThrows(IllegalArgumentException.class, () -> service.startRecording("", null));
        assertThrows(IllegalArgumentException.class, () -> service.startRecording(null, null));
    }

    @Test
    void capture_throwsOnNullRequest() {
        service.startRecording(PLAN_ID, null);
        assertThrows(IllegalArgumentException.class, () -> service.capture(PLAN_ID, null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CapturedRequest makeRequest(String method, String url) {
        return new CapturedRequest(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                method,
                url,
                Map.of(),
                new byte[0],
                200,
                0L
        );
    }
}
