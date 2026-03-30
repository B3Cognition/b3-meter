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
package com.b3meter.web.api.service;

import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Business logic for the proxy recorder.
 *
 * <p>For v1 the service does NOT run a real MITM proxy server. Instead it:
 * <ul>
 *   <li>Stores a {@link ProxyRecorderConfig} per plan while recording is active.</li>
 *   <li>Accepts captured requests pushed by external agents (browser extension,
 *       curl, or a separate proxy sidecar) via {@link #capture}.</li>
 *   <li>Filters captured requests against include/exclude patterns before storage.</li>
 *   <li>Converts the captured list to {@code TestPlanNode}-shaped maps that can be
 *       serialised and merged into the test plan tree by the caller.</li>
 * </ul>
 *
 * <p>All state is in-memory and scoped to the JVM process lifetime. Multiple plans
 * can record concurrently.
 */
@Service
public class ProxyRecorderService {

    // -------------------------------------------------------------------------
    // Internal session state
    // -------------------------------------------------------------------------

    private static final class RecordingSession {
        final ProxyRecorderConfig config;
        final List<CapturedRequest> captured = new ArrayList<>();
        final List<Pattern> includePatterns;
        final List<Pattern> excludePatterns;

        RecordingSession(ProxyRecorderConfig config) {
            this.config = config;
            this.includePatterns = compilePatterns(config.includePatterns());
            this.excludePatterns = compilePatterns(config.excludePatterns());
        }

        private static List<Pattern> compilePatterns(List<String> patterns) {
            if (patterns == null || patterns.isEmpty()) {
                return List.of();
            }
            List<Pattern> compiled = new ArrayList<>(patterns.size());
            for (String p : patterns) {
                try {
                    compiled.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ignored) {
                    // Skip invalid patterns; callers should validate input.
                }
            }
            return compiled;
        }

        boolean isAllowed(String url) {
            // Exclude patterns take priority.
            for (Pattern ex : excludePatterns) {
                if (ex.matcher(url).matches()) {
                    return false;
                }
            }
            // If no include patterns, accept everything not excluded.
            if (includePatterns.isEmpty()) {
                return true;
            }
            // At least one include pattern must match.
            for (Pattern inc : includePatterns) {
                if (inc.matcher(url).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Active recording sessions keyed by plan identifier. */
    private final ConcurrentHashMap<String, RecordingSession> sessions = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts a recording session for the given plan.
     *
     * <p>If a session is already active for this plan it is silently replaced
     * (captured requests from the previous session are discarded).
     *
     * @param planId the plan to record for; must not be blank
     * @param config recorder configuration; defaults are applied for null values
     * @throws IllegalArgumentException if {@code planId} is blank
     */
    public void startRecording(String planId, ProxyRecorderConfig config) {
        requireNonBlank(planId, "planId");
        ProxyRecorderConfig effective = config != null ? config : ProxyRecorderConfig.defaultConfig();
        sessions.put(planId, new RecordingSession(effective));
    }

    /**
     * Stops the recording session for the given plan.
     *
     * <p>Captured requests are retained until {@link #getCaptured} or
     * {@link #applyToTestPlan} is called; subsequent calls to {@link #capture}
     * will be ignored once stopped.
     *
     * @param planId the plan identifier; must not be blank
     */
    public void stopRecording(String planId) {
        requireNonBlank(planId, "planId");
        sessions.remove(planId);
    }

    /**
     * Returns {@code true} when a recording session is currently active for the plan.
     *
     * @param planId the plan identifier; must not be blank
     */
    public boolean isRecording(String planId) {
        requireNonBlank(planId, "planId");
        return sessions.containsKey(planId);
    }

    /**
     * Returns the active recorder configuration for a plan, or
     * {@link ProxyRecorderConfig#defaultConfig()} when no session is active.
     *
     * @param planId the plan identifier; must not be blank
     */
    public ProxyRecorderConfig getConfig(String planId) {
        requireNonBlank(planId, "planId");
        RecordingSession session = sessions.get(planId);
        return session != null ? session.config : ProxyRecorderConfig.defaultConfig();
    }

    /**
     * Submits a captured request for storage.
     *
     * <p>The request is only stored when a recording session is active for the plan
     * and the URL passes the include/exclude filter. Returns {@code true} if stored.
     *
     * @param planId  the plan identifier; must not be blank
     * @param request the captured request; must not be null
     * @return {@code true} if the request was accepted and stored
     * @throws IllegalArgumentException if {@code planId} is blank or {@code request} is null
     */
    public boolean capture(String planId, CapturedRequest request) {
        requireNonBlank(planId, "planId");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        RecordingSession session = sessions.get(planId);
        if (session == null) {
            return false;
        }
        if (!session.isAllowed(request.url())) {
            return false;
        }
        synchronized (session.captured) {
            session.captured.add(request);
        }
        return true;
    }

    /**
     * Returns a snapshot of all captured requests for the plan.
     *
     * <p>Returns an empty list when no session is active.
     *
     * @param planId the plan identifier; must not be blank
     */
    public List<CapturedRequest> getCaptured(String planId) {
        requireNonBlank(planId, "planId");
        RecordingSession session = sessions.get(planId);
        if (session == null) {
            return List.of();
        }
        synchronized (session.captured) {
            return List.copyOf(session.captured);
        }
    }

    /**
     * Converts captured requests into {@code TestPlanNode}-shaped maps suitable
     * for merging into the JSON test plan tree.
     *
     * <p>Each captured request becomes an {@code HTTPSampler} node whose
     * {@code properties} map mirrors the JMeter HTTPSampler element fields.
     * Header Manager child nodes are added when headers are present.
     *
     * <p>The returned list may be empty if no requests have been captured.
     *
     * @param planId the plan identifier; must not be blank
     * @return list of node maps, one per captured request
     */
    public List<Map<String, Object>> applyToTestPlan(String planId) {
        requireNonBlank(planId, "planId");
        List<CapturedRequest> snapshot = getCaptured(planId);
        List<Map<String, Object>> nodes = new ArrayList<>(snapshot.size());
        for (CapturedRequest req : snapshot) {
            nodes.add(toHttpSamplerNode(req));
        }
        return nodes;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a captured request to an HTTPSampler node map.
     *
     * <p>The map structure matches the {@code TestPlanNode} TypeScript interface:
     * {@code { id, type, name, enabled, properties, children }}.
     */
    private Map<String, Object> toHttpSamplerNode(CapturedRequest req) {
        Map<String, Object> props = new HashMap<>();
        props.put("method", req.method());
        props.put("responseCode", req.responseCode());
        props.put("elapsedMs", req.elapsedMs());

        // Parse URL into protocol / server / port / path components.
        try {
            URL parsed = URI.create(req.url()).toURL();
            props.put("protocol", parsed.getProtocol());
            props.put("server", parsed.getHost());
            props.put("port", parsed.getPort() == -1 ? "" : String.valueOf(parsed.getPort()));
            String pathAndQuery = parsed.getPath();
            if (parsed.getQuery() != null) {
                pathAndQuery = pathAndQuery + "?" + parsed.getQuery();
            }
            props.put("path", pathAndQuery);
        } catch (MalformedURLException e) {
            props.put("url", req.url());
        }

        // Body as UTF-8 string when available.
        if (req.body() != null && req.body().length > 0) {
            props.put("body", new String(req.body(), java.nio.charset.StandardCharsets.UTF_8));
        }

        // Header Manager child node.
        List<Map<String, Object>> children = new ArrayList<>();
        if (!req.headers().isEmpty()) {
            children.add(toHeaderManagerNode(req.id(), req.headers()));
        }

        String displayName = req.method() + " " + req.url();
        if (displayName.length() > 80) {
            displayName = displayName.substring(0, 77) + "...";
        }

        Map<String, Object> node = new HashMap<>();
        node.put("id", "captured-" + req.id());
        node.put("type", "HTTPSampler");
        node.put("name", displayName);
        node.put("enabled", true);
        node.put("properties", props);
        node.put("children", children);
        return node;
    }

    /** Builds an HTTP Header Manager node from a headers map. */
    private Map<String, Object> toHeaderManagerNode(String parentId, Map<String, String> headers) {
        Map<String, Object> props = new HashMap<>();
        props.put("headers", headers);

        Map<String, Object> node = new HashMap<>();
        node.put("id", "headers-" + parentId);
        node.put("type", "HeaderManager");
        node.put("name", "HTTP Header Manager");
        node.put("enabled", true);
        node.put("properties", props);
        node.put("children", List.of());
        return node;
    }

    /** Throws {@link IllegalArgumentException} when a required string argument is blank. */
    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    // -------------------------------------------------------------------------
    // Factory helper for callers that need to construct a CapturedRequest
    // -------------------------------------------------------------------------

    /**
     * Convenience factory that generates a random ID and current timestamp.
     *
     * @param method       HTTP method
     * @param url          full URL
     * @param headers      request headers (may be empty)
     * @param body         request body bytes (may be empty)
     * @param responseCode HTTP response status code
     * @param elapsedMs    round-trip time in milliseconds
     * @return a new {@link CapturedRequest} instance
     */
    public static CapturedRequest newCapturedRequest(
            String method,
            String url,
            Map<String, String> headers,
            byte[] body,
            int responseCode,
            long elapsedMs) {
        return new CapturedRequest(
                UUID.randomUUID().toString(),
                Instant.now(),
                method,
                url,
                headers != null ? headers : Map.of(),
                body != null ? body : new byte[0],
                responseCode,
                elapsedMs
        );
    }
}
