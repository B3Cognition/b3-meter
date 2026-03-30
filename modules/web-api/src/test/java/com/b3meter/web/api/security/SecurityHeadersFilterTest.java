package com.jmeternext.web.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.jmeternext.web.api.security.SecurityHeadersFilter.CSP_VALUE;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HEADER_CSP;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HEADER_HSTS;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HEADER_RP;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HEADER_XCTO;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HEADER_XFO;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.HSTS_VALUE;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.RP_VALUE;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.XCTO_VALUE;
import static com.jmeternext.web.api.security.SecurityHeadersFilter.XFO_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SecurityHeadersFilter}.
 *
 * <p>Uses Spring's {@link MockHttpServletRequest} / {@link MockHttpServletResponse}
 * so no servlet container or Spring context is required.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private MockHttpServletRequest  request;
    private MockHttpServletResponse response;
    private MockFilterChain         chain;

    @BeforeEach
    void setUp() {
        filter   = new SecurityHeadersFilter();
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain    = new MockFilterChain();
    }

    // -------------------------------------------------------------------------
    // Individual header assertions
    // -------------------------------------------------------------------------

    @Test
    void doFilter_setsCspHeader() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertEquals(CSP_VALUE, response.getHeader(HEADER_CSP),
                "Content-Security-Policy header must be set");
    }

    @Test
    void doFilter_cspContainsDefaultSrcSelf() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"),
                "CSP must include default-src 'self'");
    }

    @Test
    void doFilter_cspContainsScriptSrcSelf() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("script-src 'self'"),
                "CSP must include script-src 'self'");
    }

    @Test
    void doFilter_cspBlocksObjects() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("object-src 'none'"),
                "CSP must block object-src");
    }

    @Test
    void doFilter_cspRestrictsBaseUri() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("base-uri 'self'"),
                "CSP must restrict base-uri");
    }

    @Test
    void doFilter_cspAllowsInlineStylesForReactSpa() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"),
                "CSP must allow inline styles for React SPA");
    }

    @Test
    void doFilter_cspAllowsDataImagesForReactSpa() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("img-src 'self' data:"),
                "CSP must allow data: URIs for images");
    }

    @Test
    void doFilter_cspAllowsWebSocketConnections() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String csp = response.getHeader(HEADER_CSP);
        assertNotNull(csp);
        assertTrue(csp.contains("connect-src 'self' ws: wss:"),
                "CSP must allow WebSocket connections for live streaming");
    }

    @Test
    void doFilter_setsXContentTypeOptionsNosniff() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertEquals(XCTO_VALUE, response.getHeader(HEADER_XCTO),
                "X-Content-Type-Options must be nosniff");
    }

    @Test
    void doFilter_setsXFrameOptionsDeny() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertEquals(XFO_VALUE, response.getHeader(HEADER_XFO),
                "X-Frame-Options must be DENY");
    }

    @Test
    void doFilter_setsStrictTransportSecurity() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertEquals(HSTS_VALUE, response.getHeader(HEADER_HSTS),
                "Strict-Transport-Security must be set");
    }

    @Test
    void doFilter_hstsIncludesSubDomains() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String hsts = response.getHeader(HEADER_HSTS);
        assertNotNull(hsts);
        assertTrue(hsts.contains("includeSubDomains"),
                "HSTS must include includeSubDomains");
    }

    @Test
    void doFilter_setsReferrerPolicy() throws Exception {
        filter.doFilterInternal(request, response, chain);
        assertEquals(RP_VALUE, response.getHeader(HEADER_RP),
                "Referrer-Policy must be set");
    }

    // -------------------------------------------------------------------------
    // Filter chain propagation
    // -------------------------------------------------------------------------

    @Test
    void doFilter_invokesNextFilterInChain() throws Exception {
        filter.doFilterInternal(request, response, chain);
        // MockFilterChain records the last request/response passed through it
        assertNotNull(chain.getRequest(),
                "Filter must call chain.doFilter() — request must propagate");
    }

    // -------------------------------------------------------------------------
    // Idempotency: existing headers are overwritten (not appended)
    // -------------------------------------------------------------------------

    @Test
    void doFilter_overwritesPreexistingCspHeader() throws Exception {
        response.addHeader(HEADER_CSP, "default-src *");   // permissive attacker-set value

        filter.doFilterInternal(request, response, chain);

        // setHeader replaces, so only one value should remain (the strict one)
        assertEquals(CSP_VALUE, response.getHeader(HEADER_CSP),
                "Pre-existing CSP header must be replaced by the strict policy");
    }
}
