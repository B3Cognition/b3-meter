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

import com.b3meter.web.api.repository.TestPlanRepository;
import com.b3meter.web.api.repository.TestRunRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that enforces resource-ownership rules for test plans and
 * test runs.
 *
 * <p>A user may only access resources they own. An {@code ADMIN} role bypasses
 * the ownership check entirely.
 *
 * <p>URL patterns covered:
 * <ul>
 *   <li>{@code /api/v1/plans/{id}/**} — checks {@code test_plans.owner_id}
 *   <li>{@code /api/v1/runs/{id}/**}  — checks {@code test_runs.owner_id}
 * </ul>
 *
 * <p>Requests that do not match these patterns pass through unchanged. Requests
 * by unauthenticated principals are also passed through so that the upstream
 * {@link org.springframework.security.web.access.ExceptionTranslationFilter}
 * can emit the correct 401 response.
 */
@Component
public class ResourceOwnershipFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResourceOwnershipFilter.class);

    private static final Pattern PLAN_PATTERN =
            Pattern.compile("^/api/v1/plans/([^/]+)(/.*)?$");
    private static final Pattern RUN_PATTERN  =
            Pattern.compile("^/api/v1/runs/([^/]+)(/.*)?$");

    private static final SimpleGrantedAuthority ROLE_ADMIN =
            new SimpleGrantedAuthority("ROLE_ADMIN");

    private final TestPlanRepository planRepository;
    private final TestRunRepository  runRepository;

    public ResourceOwnershipFilter(TestPlanRepository planRepository,
                                   TestRunRepository  runRepository) {
        this.planRepository = planRepository;
        this.runRepository  = runRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Pass through: unauthenticated requests are handled by security rules
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Admins bypass ownership checks
        if (auth.getAuthorities().contains(ROLE_ADMIN)) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestedUserId = (String) auth.getPrincipal();
        String path            = request.getRequestURI();

        Matcher planMatcher = PLAN_PATTERN.matcher(path);
        if (planMatcher.matches()) {
            String resourceId = planMatcher.group(1);
            if (!ownsTestPlan(requestedUserId, resourceId)) {
                log.warn("User '{}' attempted to access plan '{}' — denied",
                        requestedUserId, resourceId);
                writeForbiddenResponse(response);
                return;
            }
        }

        Matcher runMatcher = RUN_PATTERN.matcher(path);
        if (runMatcher.matches()) {
            String resourceId = runMatcher.group(1);
            if (!ownsTestRun(requestedUserId, resourceId)) {
                log.warn("User '{}' attempted to access run '{}' — denied",
                        requestedUserId, resourceId);
                writeForbiddenResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean ownsTestPlan(String userId, String planId) {
        return planRepository.findById(planId)
                .map(plan -> userId.equals(plan.ownerId()))
                .orElse(true); // let 404 be handled by the controller
    }

    private boolean ownsTestRun(String userId, String runId) {
        return runRepository.findById(runId)
                .map(run -> userId.equals(run.ownerId()))
                .orElse(true); // let 404 be handled by the controller
    }

    /**
     * Writes a 403 Forbidden response directly to the response output stream,
     * bypassing {@code response.sendError()} to avoid a servlet container
     * error-dispatch cycle that could reset the HTTP status through the
     * Security filter chain.
     *
     * @param response the HTTP response to write to
     * @throws IOException if writing fails
     */
    private static void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"You do not have access to this resource\"}");
        response.getWriter().flush();
    }
}
