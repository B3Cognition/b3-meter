package com.jmeternext.web.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Service responsible for generating and validating RS256 JWT access tokens.
 *
 * <p>An RSA-2048 key pair is generated automatically on first use. In a
 * production deployment the key pair should be loaded from persistent storage
 * (e.g. a PEM file in the data directory) so tokens survive restarts. This
 * implementation generates a new in-memory key pair on startup, which is
 * acceptable for single-node development usage and for tests.
 *
 * <p>Access tokens have a fixed TTL of 15 minutes. Refresh token issuance and
 * storage is handled by {@link AuthService} — this class only concerns itself
 * with signing and verifying JWTs.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    /** Access-token time-to-live: 15 minutes. */
    static final long ACCESS_TOKEN_TTL_MS = 15L * 60L * 1000L;

    private static final String CLAIM_ROLE    = "role";
    private static final String CLAIM_USER_ID = "userId";
    private static final String ISSUER        = "jmeter-next";

    private final PrivateKey privateKey;
    private final PublicKey  publicKey;

    /**
     * Constructs the service, generating a fresh RS-2048 key pair.
     *
     * @throws IllegalStateException if RSA key-pair generation fails (should
     *                               never happen on any standard JVM)
     */
    public JwtTokenService() {
        KeyPair pair = generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey  = pair.getPublic();
    }

    /**
     * Package-private constructor that accepts an externally supplied key pair.
     * Used by tests to inject a deterministic key pair.
     *
     * @param keyPair the RSA key pair to use for signing and verification
     */
    JwtTokenService(KeyPair keyPair) {
        this.privateKey = keyPair.getPrivate();
        this.publicKey  = keyPair.getPublic();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a signed RS256 JWT access token.
     *
     * @param userId the subject identifier (user UUID)
     * @param role   the user's role (e.g. {@code "ADMIN"} or {@code "USER"})
     * @return compact JWT string
     */
    public String generateAccessToken(String userId, String role) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(ACCESS_TOKEN_TTL_MS);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Validates a compact JWT string and extracts its claims.
     *
     * @param token the compact JWT
     * @return parsed claims, or {@link Optional#empty()} if the token is
     *         invalid, expired, or tampered
     */
    public Optional<Claims> validateAndExtract(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            return Optional.empty();
        } catch (JwtException e) {
            log.debug("JWT token invalid: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the user identifier ({@code userId} claim) from a valid token.
     *
     * @param token the compact JWT
     * @return user-id string, or {@link Optional#empty()} if the token is invalid
     */
    public Optional<String> extractUserId(String token) {
        return validateAndExtract(token)
                .map(claims -> claims.get(CLAIM_USER_ID, String.class));
    }

    /**
     * Extracts the role claim from a valid token.
     *
     * @param token the compact JWT
     * @return role string, or {@link Optional#empty()} if the token is invalid
     */
    public Optional<String> extractRole(String token) {
        return validateAndExtract(token)
                .map(claims -> claims.get(CLAIM_ROLE, String.class));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available — JVM is non-standard", e);
        }
    }
}
