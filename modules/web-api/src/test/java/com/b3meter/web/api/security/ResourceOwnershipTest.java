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
package com.b3meter.web.api.security;

import com.b3meter.web.api.controller.dto.CreatePlanRequest;
import com.b3meter.web.api.controller.dto.TestPlanDto;
import com.b3meter.web.api.repository.TestPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for resource-ownership enforcement (IDOR protection).
 *
 * <p>These tests run with {@code jmeter.auth.multi-user=true} so that the
 * {@link ResourceOwnershipFilter} and {@link JwtAuthenticationFilter} are
 * active. Two users are seeded: a regular user (USER role) and an admin
 * (ADMIN role).
 *
 * <p>Assertions:
 * <ul>
 *   <li>User A cannot read, update, or delete User B's test plans (403)
 *   <li>Admin can access any plan regardless of ownership
 *   <li>Unauthenticated requests to protected endpoints get 401
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "jmeter.auth.multi-user=true")
class ResourceOwnershipTest {

    private static final String USER_A_PASSWORD = "password-user-a";
    private static final String USER_B_PASSWORD = "password-user-b";
    private static final String ADMIN_PASSWORD  = "password-admin";

    private String userAId;
    private String userBId;
    private String adminId;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestPlanRepository planRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @BeforeEach
    void seedUsers() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        userAId = seedUser("ownership-user-a", "a@example.com", "USER",
                encoder.encode(USER_A_PASSWORD));
        userBId = seedUser("ownership-user-b", "b@example.com", "USER",
                encoder.encode(USER_B_PASSWORD));
        adminId = seedUser("ownership-admin",  "admin@example.com", "ADMIN",
                encoder.encode(ADMIN_PASSWORD));
    }

    // -------------------------------------------------------------------------
    // Unauthenticated access
    // -------------------------------------------------------------------------

    @Test
    void unauthenticated_accessToProtectedEndpoint_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/plans", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // User A cannot access User B's resources
    // -------------------------------------------------------------------------

    @Test
    void userA_cannotReadUserB_plan_returns403() {
        // Create a plan owned by User B
        String planId = createPlanAs(userBId, "User B Plan");

        // User A tries to read it
        ResponseEntity<String> response = getWithToken(
                "/api/v1/plans/" + planId,
                tokenFor(userAId, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void userA_cannotUpdateUserB_plan_returns403() {
        String planId = createPlanAs(userBId, "User B Update Target");

        Map<String, String> body = Map.of("name", "Hijacked Name");
        ResponseEntity<String> response = exchangeWithToken(
                "/api/v1/plans/" + planId,
                HttpMethod.PUT,
                body,
                tokenFor(userAId, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void userA_cannotDeleteUserB_plan_returns403() {
        String planId = createPlanAs(userBId, "User B Delete Target");

        ResponseEntity<String> response = exchangeWithToken(
                "/api/v1/plans/" + planId,
                HttpMethod.DELETE,
                null,
                tokenFor(userAId, "USER"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // User A can access their own resources
    // -------------------------------------------------------------------------

    @Test
    void userA_canReadOwnPlan_returns200() {
        String planId = createPlanAs(userAId, "User A Own Plan");

        ResponseEntity<TestPlanDto> response = getWithToken(
                "/api/v1/plans/" + planId,
                tokenFor(userAId, "USER"),
                TestPlanDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User A Own Plan", response.getBody().name());
    }

    // -------------------------------------------------------------------------
    // Admin can access any resource
    // -------------------------------------------------------------------------

    @Test
    void admin_canReadUserB_plan_returns200() {
        String planId = createPlanAs(userBId, "User B Plan For Admin");

        ResponseEntity<TestPlanDto> response = getWithToken(
                "/api/v1/plans/" + planId,
                tokenFor(adminId, "ADMIN"),
                TestPlanDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // -------------------------------------------------------------------------
    // Admin-only endpoint enforcement
    // -------------------------------------------------------------------------

    @Test
    void regularUser_cannotPostToPlugins_returns403() {
        ResponseEntity<String> response = exchangeWithToken(
                "/api/v1/plugins/some-plugin",
                HttpMethod.POST,
                Map.of("name", "test"),
                tokenFor(userAId, "USER"));

        // 403 from Spring Security role check (plugins endpoint not implemented,
        // but the security rule fires before routing)
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String seedUser(String username, String email, String role, String passwordHash) {
        if (userRepository.findByUsername(username).isPresent()) {
            return userRepository.findByUsername(username).get().id();
        }
        String id = UUID.randomUUID().toString();
        userRepository.save(new UserEntity(id, username, email, role, passwordHash, Instant.now()));
        return id;
    }

    private String createPlanAs(String ownerId, String planName) {
        com.b3meter.web.api.repository.TestPlanEntity entity =
                new com.b3meter.web.api.repository.TestPlanEntity(
                        UUID.randomUUID().toString(),
                        planName,
                        ownerId,
                        "{}",
                        Instant.now(),
                        Instant.now(),
                        null
                );
        planRepository.save(entity);
        return entity.id();
    }

    private String tokenFor(String userId, String role) {
        return jwtTokenService.generateAccessToken(userId, role);
    }

    private <T> ResponseEntity<T> getWithToken(String url, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    }

    private ResponseEntity<String> getWithToken(String url, String token) {
        return getWithToken(url, token, String.class);
    }

    private ResponseEntity<String> exchangeWithToken(String url, HttpMethod method,
                                                      Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Content-Type", "application/json");
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, method, entity, String.class);
    }
}
