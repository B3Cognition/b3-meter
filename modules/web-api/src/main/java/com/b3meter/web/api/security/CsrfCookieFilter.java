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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that surfaces the Spring Security {@link CsrfToken} as an
 * HTTP cookie ({@code XSRF-TOKEN}) so that a React SPA can read it and
 * echo it back as an {@code X-XSRF-TOKEN} request header.
 *
 * <p>The cookie is intentionally <em>not</em> {@code HttpOnly} so that
 * JavaScript running on the same origin can access it. The server-side
 * CSRF filter validates the {@code X-XSRF-TOKEN} header against the token
 * stored in the session / request attribute, providing the Double Submit
 * Cookie protection pattern.
 *
 * <p>This filter does nothing if no {@link CsrfToken} is present in the
 * request (e.g. when CSRF protection is disabled for the stateless JWT
 * API path).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "XSRF-TOKEN";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        CsrfToken csrfToken =
                (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            // Render the lazy token value so Spring regenerates it if needed
            String tokenValue = csrfToken.getToken();

            Cookie cookie = new Cookie(COOKIE_NAME, tokenValue);
            cookie.setPath("/");
            cookie.setSecure(request.isSecure());
            // NOT HttpOnly — the SPA must be able to read this with JS
            cookie.setHttpOnly(false);
            // SameSite=Strict provides additional CSRF protection
            response.addHeader("Set-Cookie",
                    COOKIE_NAME + "=" + tokenValue
                    + "; Path=/"
                    + (request.isSecure() ? "; Secure" : "")
                    + "; SameSite=Strict");
        }

        filterChain.doFilter(request, response);
    }
}
