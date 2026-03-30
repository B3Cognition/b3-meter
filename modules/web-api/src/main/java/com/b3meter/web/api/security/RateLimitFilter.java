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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate-limits failed login attempts per IP address.
 * After {@value #MAX_FAILED_ATTEMPTS} failures within {@value #WINDOW_SECONDS}s,
 * the IP is locked out for {@value #LOCKOUT_SECONDS}s.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final int MAX_FAILED_ATTEMPTS = 10;
    static final long WINDOW_SECONDS = 300;
    static final long LOCKOUT_SECONDS = 900;

    private final ConcurrentHashMap<String, LoginAttemptTracker> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!"/api/v1/auth/login".equals(path) || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        LoginAttemptTracker tracker = attempts.computeIfAbsent(ip, k -> new LoginAttemptTracker());

        if (tracker.isLocked()) {
            long retryAfter = tracker.lockoutRemainingSeconds();
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too many failed login attempts\",\"retry_after_seconds\":" + retryAfter + "}");
            return;
        }

        chain.doFilter(request, response);

        int status = response.getStatus();
        if (status == 401 || status == 403) {
            tracker.recordFailure();
        } else if (status == 200) {
            tracker.reset();
        }
    }

    ConcurrentHashMap<String, LoginAttemptTracker> getAttempts() { return attempts; }

    static class LoginAttemptTracker {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile Instant windowStart = Instant.now();
        private volatile Instant lockedUntil = null;

        void recordFailure() {
            Instant now = Instant.now();
            if (windowStart.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                failureCount.set(1);
                windowStart = now;
                lockedUntil = null;
            } else {
                int count = failureCount.incrementAndGet();
                if (count >= MAX_FAILED_ATTEMPTS) {
                    lockedUntil = now.plusSeconds(LOCKOUT_SECONDS);
                }
            }
        }

        boolean isLocked() {
            Instant locked = lockedUntil;
            if (locked == null) return false;
            if (Instant.now().isAfter(locked)) { reset(); return false; }
            return true;
        }

        long lockoutRemainingSeconds() {
            Instant locked = lockedUntil;
            if (locked == null) return 0;
            return Math.max(0, locked.getEpochSecond() - Instant.now().getEpochSecond());
        }

        void reset() {
            failureCount.set(0);
            windowStart = Instant.now();
            lockedUntil = null;
        }

        int getFailureCount() { return failureCount.get(); }
    }
}
