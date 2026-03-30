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

import java.util.List;

/**
 * Configuration for a proxy recorder session.
 *
 * <p>Controls the port the proxy listens on, optional target URL override,
 * regex patterns for including or excluding captured URLs, and flags for
 * whether request headers and bodies should be captured.
 *
 * <p>Default exclusion patterns filter out common static assets (images,
 * stylesheets, scripts, fonts, and browser icons).
 */
public record ProxyRecorderConfig(
        int port,
        String targetBaseUrl,
        List<String> includePatterns,
        List<String> excludePatterns,
        boolean captureHeaders,
        boolean captureBody
) {

    /** Default proxy port. */
    public static final int DEFAULT_PORT = 8888;

    /**
     * Default exclusion patterns — filters static assets that are generally
     * not interesting for load-test replay.
     */
    public static final List<String> DEFAULT_EXCLUDE_PATTERNS = List.of(
            ".*\\.(gif|jpg|jpeg|png|css|js|ico|woff|woff2|ttf|svg|webp)(\\?.*)?$"
    );

    /**
     * Returns a default configuration with port {@value #DEFAULT_PORT},
     * no target override, no include patterns, default exclusions,
     * headers captured, and bodies captured.
     */
    public static ProxyRecorderConfig defaultConfig() {
        return new ProxyRecorderConfig(
                DEFAULT_PORT,
                null,
                List.of(),
                DEFAULT_EXCLUDE_PATTERNS,
                true,
                true
        );
    }
}
