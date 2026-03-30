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
package com.b3meter.web.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ObservabilityConfig}.
 *
 * <p>Boots the full Spring context on a random port and verifies that:
 * <ul>
 *   <li>{@code GET /actuator/prometheus} responds with HTTP 200 and
 *       Prometheus text-format content.</li>
 *   <li>Custom {@code jmeter_*} metrics are present in the scrape output.</li>
 *   <li>The health endpoint is unaffected by the observability configuration.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoints.web.exposure.include=*",
            "management.endpoint.prometheus.enabled=true",
            "management.prometheus.metrics.export.enabled=true"
        }
)
class ObservabilityConfigTest {

    @Autowired
    TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // /actuator/prometheus
    // -------------------------------------------------------------------------

    @Test
    void prometheusEndpointReturnsOk() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, response.getStatusCode().value(),
                "/actuator/prometheus must return HTTP 200");
    }

    @Test
    void prometheusEndpointBodyIsNotEmpty() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertNotNull(response.getBody(), "Prometheus scrape body must not be null");
        assertTrue(response.getBody().length() > 0,
                "Prometheus scrape body must not be empty");
    }

    // -------------------------------------------------------------------------
    // Custom jmeter_* metrics
    // -------------------------------------------------------------------------

    @Test
    void prometheusContainsJmeterRunsActive() {
        String body = scrapePrometheus();
        assertTrue(body.contains("jmeter_runs_active"),
                "Prometheus output must contain jmeter_runs_active gauge");
    }

    @Test
    void prometheusContainsJmeterSamplesTotal() {
        String body = scrapePrometheus();
        assertTrue(body.contains("jmeter_samples_total"),
                "Prometheus output must contain jmeter_samples_total counter");
    }

    @Test
    void prometheusContainsJmeterErrorsTotal() {
        String body = scrapePrometheus();
        assertTrue(body.contains("jmeter_errors_total"),
                "Prometheus output must contain jmeter_errors_total counter");
    }

    @Test
    void prometheusContainsJmeterRunDurationSeconds() {
        String body = scrapePrometheus();
        assertTrue(body.contains("jmeter_run_duration_seconds"),
                "Prometheus output must contain jmeter_run_duration_seconds timer");
    }

    // -------------------------------------------------------------------------
    // Health endpoint still works
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void healthEndpointStillReturnsOkAfterObservabilityConfig() {
        ResponseEntity<java.util.Map<String, Object>> response =
                restTemplate.getForEntity("/api/v1/health",
                        (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
        assertEquals(200, response.getStatusCode().value(),
                "health endpoint must still return HTTP 200 after observability config is loaded");
        assertNotNull(response.getBody(), "health response body must not be null");
        assertEquals("UP", response.getBody().get("status"),
                "health status must be UP");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String scrapePrometheus() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
