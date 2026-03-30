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
package com.b3meter.web.api.controller;

import com.b3meter.web.api.security.UserEntity;
import com.b3meter.web.api.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for {@link AuthController}.
 *
 * <p>Boots the full Spring context on a random port. A test user is inserted
 * directly via {@link UserRepository} before each test so no existing data is
 * required.
 *
 * <p>The application runs in single-user mode by default (all requests allowed).
 * These tests exercise the auth endpoints directly regardless of mode.
 */
@SuppressWarnings("rawtypes")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {

    private static final String TEST_USERNAME = "auth-test-user";
    private static final String TEST_PASSWORD = "correct-horse-battery-staple";
    private static final String TEST_USER_ID  = UUID.randomUUID().toString();

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void seedTestUser() {
        // Insert test user only if not already present (tests may run in any order)
        if (userRepository.findByUsername(TEST_USERNAME).isEmpty()) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
            UserEntity user = new UserEntity(
                    TEST_USER_ID,
                    TEST_USERNAME,
                    "auth-test@example.com",
                    "USER",
                    encoder.encode(TEST_PASSWORD),
                    Instant.now()
            );
            userRepository.save(user);
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithTokens() {
        Map<String, String> body = Map.of(
                "username", TEST_USERNAME,
                "password", TEST_PASSWORD
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(      //NOSONAR raw Map needed for TestRestTemplate
                "/api/v1/auth/login", body, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("accessToken"),
                "response must include accessToken");
        assertNotNull(response.getBody().get("refreshToken"),
                "response must include refreshToken");
    }

    @Test
    void login_wrongPassword_returns401() {
        Map<String, String> body = Map.of(
                "username", TEST_USERNAME,
                "password", "wrong-password"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(      //NOSONAR
                "/api/v1/auth/login", body, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_unknownUser_returns401() {
        Map<String, String> body = Map.of(
                "username", "nobody@nowhere.invalid",
                "password", "any-password"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(      //NOSONAR
                "/api/v1/auth/login", body, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_validRefreshToken_returns200WithNewTokens() {
        // Step 1: login to obtain an initial refresh token
        Map<String, String> loginBody = Map.of(
                "username", TEST_USERNAME,
                "password", TEST_PASSWORD
        );
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity( //NOSONAR
                "/api/v1/auth/login", loginBody, Map.class);
        assertNotNull(loginResponse.getBody());
        String refreshToken = (String) loginResponse.getBody().get("refreshToken");

        // Step 2: use the refresh token to get a new pair
        Map<String, String> refreshBody = Map.of("refreshToken", refreshToken);
        ResponseEntity<Map> response = restTemplate.postForEntity(      //NOSONAR
                "/api/v1/auth/refresh", refreshBody, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("accessToken"),
                "refresh response must include new accessToken");
        assertNotNull(response.getBody().get("refreshToken"),
                "refresh response must include new refreshToken");
    }

    @Test
    void refresh_invalidRefreshToken_returns401() {
        Map<String, String> body = Map.of("refreshToken", "completely-invalid-token");

        ResponseEntity<Map> response = restTemplate.postForEntity(      //NOSONAR
                "/api/v1/auth/refresh", body, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refresh_usedRefreshToken_returns401OnSecondUse() {
        // Step 1: login
        Map<String, String> loginBody = Map.of(
                "username", TEST_USERNAME,
                "password", TEST_PASSWORD
        );
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity( //NOSONAR
                "/api/v1/auth/login", loginBody, Map.class);
        assertNotNull(loginResponse.getBody());
        String refreshToken = (String) loginResponse.getBody().get("refreshToken");

        // Step 2: first use — succeeds
        Map<String, String> body = Map.of("refreshToken", refreshToken);
        restTemplate.postForEntity("/api/v1/auth/refresh", body, Map.class);

        // Step 3: second use of the same token — must be rejected (single-use)
        ResponseEntity<Map> secondUse = restTemplate.postForEntity(     //NOSONAR
                "/api/v1/auth/refresh", body, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, secondUse.getStatusCode(),
                "refresh token must be single-use; second call must return 401");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/logout
    // -------------------------------------------------------------------------

    @Test
    void logout_validRefreshToken_returns204AndInvalidatesToken() {
        // Login to get tokens
        Map<String, String> loginBody = Map.of(
                "username", TEST_USERNAME,
                "password", TEST_PASSWORD
        );
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity( //NOSONAR
                "/api/v1/auth/login", loginBody, Map.class);
        assertNotNull(loginResponse.getBody());
        String refreshToken = (String) loginResponse.getBody().get("refreshToken");

        // Logout
        Map<String, String> logoutBody = Map.of("refreshToken", refreshToken);
        ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
                "/api/v1/auth/logout", logoutBody, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.getStatusCode());

        // Verify token is no longer usable
        ResponseEntity<Map> refreshAttempt = restTemplate.postForEntity( //NOSONAR
                "/api/v1/auth/refresh", logoutBody, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, refreshAttempt.getStatusCode(),
                "refresh token must be invalidated after logout");
    }

    @Test
    void logout_unknownToken_returns204WithoutError() {
        // Logout with an unknown token must silently succeed (no information leak)
        Map<String, String> body = Map.of("refreshToken", "unknown-token-value");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/auth/logout", body, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
