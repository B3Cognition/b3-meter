package com.jmeternext.web.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the web API backend.
 *
 * <p>Serves REST endpoints and SSE streams to the React frontend.
 * Delegates all test execution concerns to injected {@code UIBridge} and
 * related engine-service interfaces.
 */
@SpringBootApplication
public class WebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApiApplication.class, args);
    }
}
