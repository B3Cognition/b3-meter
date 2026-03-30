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
package com.b3meter.web.api.config;

import com.b3meter.web.api.security.JwtAuthenticationFilter;
import com.b3meter.web.api.security.ResourceOwnershipFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration supporting both single-user desktop mode and
 * multi-user (cloud) mode.
 *
 * <p>The active mode is controlled by the {@code jmeter.auth.multi-user}
 * property (default: {@code false}).
 *
 * <h2>Single-user desktop mode</h2>
 * <p>All requests are permitted without authentication. CSRF protection is
 * disabled because this backend is consumed by a same-origin SPA with no
 * session-based authentication surface.
 *
 * <h2>Multi-user mode</h2>
 * <p>Endpoints are protected as follows:
 * <ul>
 *   <li>{@code /api/v1/auth/**} — public (login, refresh, logout)
 *   <li>{@code /api/v1/health} — public
 *   <li>{@code /actuator/**}   — public (should be firewalled at the infra level)
 *   <li>{@code POST /api/v1/plugins/**}, {@code DELETE /api/v1/plugins/**} — ADMIN only
 *   <li>{@code POST /api/v1/workers/**}, {@code DELETE /api/v1/workers/**} — ADMIN only
 *   <li>Everything else — authenticated (any role)
 * </ul>
 *
 * <p>JWT Bearer token validation is performed by {@link JwtAuthenticationFilter};
 * resource-ownership checks (IDOR protection) are enforced by
 * {@link ResourceOwnershipFilter}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final boolean multiUserEnabled;
    private final JwtAuthenticationFilter  jwtFilter;
    private final ResourceOwnershipFilter  ownershipFilter;

    public SecurityConfig(
            org.springframework.core.env.Environment env,
            JwtAuthenticationFilter  jwtFilter,
            ResourceOwnershipFilter  ownershipFilter
    ) {
        this.multiUserEnabled = Boolean.parseBoolean(
                env.getProperty("jmeter.auth.multi-user", "false"));
        this.jwtFilter       = jwtFilter;
        this.ownershipFilter = ownershipFilter;
    }

    /**
     * Prevents Spring Boot from auto-registering {@link JwtAuthenticationFilter}
     * as a top-level servlet filter. It is added exclusively inside the
     * Security filter chain by {@link #filterChain}.
     *
     * @param filter the JWT filter bean
     * @return a disabled registration bean
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Prevents Spring Boot from auto-registering {@link ResourceOwnershipFilter}
     * as a top-level servlet filter.
     *
     * @param filter the ownership filter bean
     * @return a disabled registration bean
     */
    @Bean
    public FilterRegistrationBean<ResourceOwnershipFilter> ownershipFilterRegistration(
            ResourceOwnershipFilter filter) {
        FilterRegistrationBean<ResourceOwnershipFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Builds the {@link SecurityFilterChain}.
     *
     * <p>When {@code jmeter.auth.multi-user=true} the chain enforces JWT Bearer
     * authentication and role-based access control. In the default single-user
     * mode all requests are permitted without a token.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured filter chain
     * @throws Exception if the HttpSecurity builder raises an error
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF is always disabled: the API is stateless (no session cookies).
        http.csrf(csrf -> csrf.disable());

        if (multiUserEnabled) {
            configureMultiUserSecurity(http);
        } else {
            configureSingleUserSecurity(http);
        }

        return http.build();
    }

    // -------------------------------------------------------------------------
    // Mode-specific configuration helpers
    // -------------------------------------------------------------------------

    private void configureSingleUserSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }

    private void configureMultiUserSecurity(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Write directly to the response stream for both 401 (unauthenticated)
            // and 403 (authenticated but forbidden). Using sendError() would trigger
            // the servlet container's error-dispatch cycle, which re-processes the
            // request through the Security filter chain and can produce unexpected
            // status codes on the client side.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    response.getWriter().flush();
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Forbidden\"}");
                    response.getWriter().flush();
                }))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // Admin-only management endpoints
                .requestMatchers(HttpMethod.POST,   "/api/v1/plugins/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,  "/api/v1/plugins/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/v1/workers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,  "/api/v1/workers/**").hasRole("ADMIN")

                // Everything else requires a valid token (any role)
                .anyRequest().authenticated()
            )
            // Insert JWT filter before Spring's UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Insert ownership filter after JWT filter (needs SecurityContext populated)
            .addFilterAfter(ownershipFilter, JwtAuthenticationFilter.class);
    }
}
