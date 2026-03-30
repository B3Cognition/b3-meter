package com.jmeternext.web.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that appends defensive HTTP security headers to every
 * response regardless of the active authentication mode.
 *
 * <h2>Headers applied</h2>
 * <ul>
 *   <li>{@code Content-Security-Policy} — restricts resource origins to
 *       {@code 'self'} and blocks plugins ({@code object-src 'none'}).</li>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} — blocks this page being embedded in
 *       a frame (clickjacking defence).</li>
 *   <li>{@code Strict-Transport-Security} — instructs browsers to use HTTPS
 *       for one year including sub-domains (only meaningful when served over
 *       TLS; harmless otherwise).</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — limits
 *       referrer leakage across origins.</li>
 * </ul>
 *
 * <p>All headers are set unconditionally. If a header is already present it
 * is <em>overwritten</em> to prevent downstream components from weakening
 * the policy.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String HEADER_CSP   = "Content-Security-Policy";
    static final String HEADER_XCTO  = "X-Content-Type-Options";
    static final String HEADER_XFO   = "X-Frame-Options";
    static final String HEADER_HSTS  = "Strict-Transport-Security";
    static final String HEADER_RP    = "Referrer-Policy";

    static final String CSP_VALUE  =
            "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "connect-src 'self' ws: wss:; "
            + "object-src 'none'; "
            + "base-uri 'self'";
    static final String XCTO_VALUE = "nosniff";
    static final String XFO_VALUE  = "DENY";
    static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";
    static final String RP_VALUE   = "strict-origin-when-cross-origin";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        response.setHeader(HEADER_CSP,  CSP_VALUE);
        response.setHeader(HEADER_XCTO, XCTO_VALUE);
        response.setHeader(HEADER_XFO,  XFO_VALUE);
        response.setHeader(HEADER_HSTS, HSTS_VALUE);
        response.setHeader(HEADER_RP,   RP_VALUE);

        filterChain.doFilter(request, response);
    }
}
