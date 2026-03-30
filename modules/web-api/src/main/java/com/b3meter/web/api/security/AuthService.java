package com.jmeternext.web.api.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for user authentication: login, token refresh, and logout.
 *
 * <p>Passwords are hashed with BCrypt (strength 12). Refresh tokens are
 * opaque random strings; only their SHA-256 digest is persisted in the
 * database (defence-in-depth against database read leaks).
 *
 * <p>Refresh tokens have a fixed TTL of 30 days.
 */
@Service
public class AuthService {

    /** Refresh-token time-to-live: 30 days. */
    static final long REFRESH_TOKEN_TTL_MS = 30L * 24L * 60L * 60L * 1000L;

    private final UserRepository    userRepository;
    private final JwtTokenService   jwtTokenService;
    private final PasswordEncoder   passwordEncoder;
    private final SecureRandom      secureRandom;

    public AuthService(UserRepository userRepository, JwtTokenService jwtTokenService) {
        this.userRepository  = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        this.secureRandom    = new SecureRandom();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Authenticates a user by username and password.
     *
     * @param username the user's username
     * @param password the user's raw (unhashed) password
     * @return a token pair on success, or {@link Optional#empty()} if the
     *         credentials are invalid
     */
    public Optional<TokenPair> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.passwordHash()))
                .map(user -> issueTokenPair(user.id(), user.role()));
    }

    /**
     * Issues a new access token in exchange for a valid refresh token.
     *
     * @param rawRefreshToken the raw refresh token received from the client
     * @return a new token pair, or {@link Optional#empty()} if the refresh
     *         token is unknown or expired
     */
    public Optional<TokenPair> refresh(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        return userRepository.findUserIdByRefreshTokenHash(hash)
                .flatMap(userId -> userRepository.findById(userId))
                .map(user -> {
                    // Rotate the refresh token on each use (single-use semantics)
                    userRepository.deleteRefreshToken(hash);
                    return issueTokenPair(user.id(), user.role());
                });
    }

    /**
     * Invalidates the given refresh token (logout from the current device).
     *
     * @param rawRefreshToken the raw refresh token to invalidate; no-op if not found
     */
    public void logout(String rawRefreshToken) {
        userRepository.deleteRefreshToken(sha256Hex(rawRefreshToken));
    }

    /**
     * Hashes a raw password with BCrypt for storage.
     *
     * @param rawPassword the plain-text password
     * @return BCrypt hash
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private TokenPair issueTokenPair(String userId, String role) {
        String accessToken   = jwtTokenService.generateAccessToken(userId, role);
        String refreshToken  = generateOpaqueToken();
        String refreshHash   = sha256Hex(refreshToken);
        Instant expiresAt    = Instant.now().plusMillis(REFRESH_TOKEN_TTL_MS);

        userRepository.saveRefreshToken(UUID.randomUUID().toString(),
                userId, refreshHash, expiresAt);

        return new TokenPair(accessToken, refreshToken);
    }

    /** Generates a 256-bit URL-safe opaque token. */
    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the SHA-256 hex digest of a string (UTF-8 encoded). */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    /**
     * A pair of access and refresh tokens returned on successful authentication.
     *
     * @param accessToken  short-lived JWT (15 min)
     * @param refreshToken long-lived opaque token (30 days)
     */
    public record TokenPair(String accessToken, String refreshToken) {}
}
