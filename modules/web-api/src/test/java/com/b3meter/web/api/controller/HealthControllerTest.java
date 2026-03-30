package com.jmeternext.web.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for {@link HealthController}.
 *
 * <p>Boots the full Spring context on a random port and exercises the
 * {@code GET /api/v1/health} endpoint via {@link TestRestTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthReturnsStatusUp() {
        var response = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void healthReturnsVersionField() {
        var response = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("version"), "version field must be present");
    }

    @Test
    void healthResponseBodyIsNotNull() {
        var response = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertNotNull(response.getBody(), "response body must not be null");
    }

    @Test
    void healthEndpointIsPublicNoAuthRequired() {
        // TestRestTemplate sends no credentials; a 401 here means security is not open
        var response = restTemplate.getForEntity("/api/v1/health", Map.class);
        assertEquals(200, response.getStatusCode().value(),
            "health endpoint must be accessible without authentication");
    }
}
