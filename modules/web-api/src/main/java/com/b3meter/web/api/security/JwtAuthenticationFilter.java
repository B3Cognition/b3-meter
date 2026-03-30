package com.jmeternext.web.api.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that extracts and validates a Bearer JWT from the
 * {@code Authorization} header on every request.
 *
 * <p>When a valid token is present the filter populates the Spring
 * {@link org.springframework.security.core.context.SecurityContext} with an
 * authenticated principal so that downstream security rules can enforce
 * role-based access control.
 *
 * <p>If the header is absent or the token is invalid the filter simply
 * passes the request through without setting a principal; subsequent
 * security rules will then reject authenticated-only endpoints with 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX      = "Bearer ";
    private static final String AUTHORIZATION_HDR  = "Authorization";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        extractBearerToken(request).ifPresent(token -> {
            Optional<Claims> claimsOpt = jwtTokenService.validateAndExtract(token);
            claimsOpt.ifPresent(claims -> {
                String userId = claims.get("userId", String.class);
                String role   = claims.get("role",   String.class);

                if (userId != null && role != null) {
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + role));

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Authenticated user '{}' with role '{}'", userId, role);
                }
            });
        });

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HDR);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
