package com.jmeternext.web.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing a lightweight health check endpoint.
 *
 * <p>Returns the application status and current version so that load balancers
 * and integration smoke tests can verify the service is live without hitting
 * Spring's Actuator infrastructure.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /** Application version string kept in sync with build.gradle.kts. */
    private static final String VERSION = "0.1.0-SNAPSHOT";

    /**
     * Returns a minimal health response.
     *
     * @return map containing {@code status} and {@code version} entries
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "version", VERSION);
    }
}
