package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.security.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for user authentication: login, token refresh, and logout.
 *
 * <p>All endpoints under {@code /api/v1/auth} are publicly accessible (no
 * Bearer token required). They are declared as permit-all in
 * {@link com.jmeternext.web.api.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------------------------

    /**
     * Authenticates a user and issues a JWT access token plus a refresh token.
     *
     * @param request JSON body with {@code username} and {@code password} fields
     * @return 200 with {@code accessToken} and {@code refreshToken}, or
     *         401 if the credentials are invalid
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password())
                .map(pair -> ResponseEntity.ok(Map.of(
                        "accessToken",  pair.accessToken(),
                        "refreshToken", pair.refreshToken()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------------------------

    /**
     * Exchanges a valid refresh token for a new access token and a rotated
     * refresh token (single-use semantics).
     *
     * @param request JSON body with a {@code refreshToken} field
     * @return 200 with new {@code accessToken} and {@code refreshToken}, or
     *         401 if the refresh token is unknown or expired
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken())
                .map(pair -> ResponseEntity.ok(Map.of(
                        "accessToken",  pair.accessToken(),
                        "refreshToken", pair.refreshToken()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/logout
    // -------------------------------------------------------------------------

    /**
     * Invalidates the supplied refresh token (logout from the current device).
     *
     * @param request JSON body with a {@code refreshToken} field
     * @return 204 No Content (always succeeds — unknown tokens are silently ignored)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Request records (package-private for testability)
    // -------------------------------------------------------------------------

    record LoginRequest(String username, String password) {}

    record RefreshRequest(String refreshToken) {}
}
