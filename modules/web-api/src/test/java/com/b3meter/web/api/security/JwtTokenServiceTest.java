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

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JwtTokenService}.
 *
 * <p>A deterministic RSA key pair is generated once per test class to keep
 * tests fast (RSA key generation is expensive). All test methods share the
 * same key pair.
 */
class JwtTokenServiceTest {

    private static final String USER_ID = "user-abc-123";
    private static final String ROLE    = "USER";

    private JwtTokenService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();
        service = new JwtTokenService(keyPair);
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    @Test
    void generateAccessToken_returnsNonBlankString() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        assertNotNull(token);
        assertFalse(token.isBlank(), "generated token must not be blank");
    }

    @Test
    void generateAccessToken_producesThreePartCompactJwt() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "compact JWT must have header.payload.signature");
    }

    // -------------------------------------------------------------------------
    // Token validation — happy path
    // -------------------------------------------------------------------------

    @Test
    void validateAndExtract_validToken_returnsClaimsPresent() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Optional<Claims> result = service.validateAndExtract(token);
        assertTrue(result.isPresent(), "valid token must produce non-empty claims");
    }

    @Test
    void validateAndExtract_validToken_claimsContainUserId() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Claims claims = service.validateAndExtract(token).orElseThrow();
        assertEquals(USER_ID, claims.get("userId", String.class));
    }

    @Test
    void validateAndExtract_validToken_claimsContainRole() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Claims claims = service.validateAndExtract(token).orElseThrow();
        assertEquals(ROLE, claims.get("role", String.class));
    }

    @Test
    void validateAndExtract_validToken_subjectMatchesUserId() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Claims claims = service.validateAndExtract(token).orElseThrow();
        assertEquals(USER_ID, claims.getSubject());
    }

    @Test
    void extractUserId_validToken_returnsUserId() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Optional<String> userId = service.extractUserId(token);
        assertTrue(userId.isPresent());
        assertEquals(USER_ID, userId.get());
    }

    @Test
    void extractRole_validToken_returnsRole() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        Optional<String> role = service.extractRole(token);
        assertTrue(role.isPresent());
        assertEquals(ROLE, role.get());
    }

    // -------------------------------------------------------------------------
    // Token validation — expired token
    // -------------------------------------------------------------------------

    @Test
    void validateAndExtract_expiredToken_returnsEmpty() throws Exception {
        // Build a token that expired in the past by manipulating the TTL via
        // a short-lived service that signs tokens expiring 1 ms ago.
        // We cannot easily travel time with the current design, so instead we
        // build an expired token manually using the JJWT builder directly.
        java.time.Instant past = java.time.Instant.now().minusSeconds(60);
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject(USER_ID)
                .claim("userId", USER_ID)
                .claim("role", ROLE)
                .issuedAt(java.util.Date.from(past.minusSeconds(30)))
                .expiration(java.util.Date.from(past))
                .signWith(pair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        // Create a service with the same key pair so the signature verifies
        // but the expiry check fails
        JwtTokenService svc = new JwtTokenService(pair);
        Optional<Claims> result = svc.validateAndExtract(expiredToken);
        assertFalse(result.isPresent(), "expired token must return empty");
    }

    // -------------------------------------------------------------------------
    // Token validation — tampered token
    // -------------------------------------------------------------------------

    @Test
    void validateAndExtract_tamperedSignature_returnsEmpty() {
        String token = service.generateAccessToken(USER_ID, ROLE);
        // Corrupt the signature segment by replacing the last few characters with
        // fixed garbage. The Base64url character set is [A-Za-z0-9_-], so replacing
        // the last 4 characters with "ZZZZ" will virtually always break the signature.
        String tampered = token.substring(0, token.length() - 4) + "ZZZZ";
        // Guard: if the constructed string happens to equal the original (astronomically
        // unlikely), skip the assertion rather than assert a false negative.
        if (!tampered.equals(token)) {
            Optional<Claims> result = service.validateAndExtract(tampered);
            assertFalse(result.isPresent(), "tampered token must return empty");
        }
    }

    @Test
    void validateAndExtract_tokenSignedByDifferentKey_returnsEmpty() throws Exception {
        // Sign with a different key pair — validation must reject it
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair otherPair = gen.generateKeyPair();
        JwtTokenService otherService = new JwtTokenService(otherPair);

        String tokenFromOtherKey = otherService.generateAccessToken(USER_ID, ROLE);
        Optional<Claims> result = service.validateAndExtract(tokenFromOtherKey);
        assertFalse(result.isPresent(), "token signed by a different key must be rejected");
    }

    @Test
    void validateAndExtract_nullToken_returnsEmpty() {
        assertFalse(service.validateAndExtract(null).isPresent());
    }

    @Test
    void validateAndExtract_blankToken_returnsEmpty() {
        assertFalse(service.validateAndExtract("   ").isPresent());
    }

    @Test
    void validateAndExtract_randomGarbage_returnsEmpty() {
        assertFalse(service.validateAndExtract("not.a.jwt").isPresent());
    }
}
