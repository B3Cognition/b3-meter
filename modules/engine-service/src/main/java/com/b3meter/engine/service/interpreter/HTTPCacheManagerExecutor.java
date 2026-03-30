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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes a {@code CacheManager} {@link PlanNode} to simulate browser HTTP caching.
 *
 * <p>Manages a per-thread cache of HTTP responses keyed by URL. Supports
 * ETag/Last-Modified conditional request headers and Expires/Cache-Control
 * max-age based cache expiration.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code clearEachIteration} — clear cache at start of each iteration</li>
 *   <li>{@code useExpires} — honour Expires/Cache-Control max-age headers</li>
 *   <li>{@code maxSize} — maximum number of cached entries (default 5000)</li>
 *   <li>{@code controlledByThread} — each VU maintains its own cache</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class HTTPCacheManagerExecutor {

    private static final Logger LOG = Logger.getLogger(HTTPCacheManagerExecutor.class.getName());

    /** Default maximum cache size. */
    private static final int DEFAULT_MAX_SIZE = 5000;

    /**
     * Thread-local cache stores for per-thread mode.
     * Key: URL string, Value: CacheEntry.
     */
    private static final ThreadLocal<ConcurrentHashMap<String, CacheEntry>> THREAD_CACHE =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * Shared cache store for global mode.
     */
    private static final ConcurrentHashMap<String, CacheEntry> GLOBAL_CACHE =
            new ConcurrentHashMap<>();

    private HTTPCacheManagerExecutor() {}

    /**
     * Configures the cache manager from the plan node properties and stores
     * configuration in the VU variable map for use by HttpSamplerExecutor.
     *
     * @param node      the CacheManager node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean clearEachIteration = node.getBoolProp("clearEachIteration", false);
        boolean useExpires = node.getBoolProp("useExpires", true);
        int maxSize = node.getIntProp("maxSize", DEFAULT_MAX_SIZE);
        boolean controlledByThread = node.getBoolProp("controlledByThread", false);

        // Store config in variables for HTTP sampler to read
        variables.put("__jmn_cache_enabled", "true");
        variables.put("__jmn_cache_clearEachIteration", String.valueOf(clearEachIteration));
        variables.put("__jmn_cache_useExpires", String.valueOf(useExpires));
        variables.put("__jmn_cache_maxSize", String.valueOf(maxSize));
        variables.put("__jmn_cache_controlledByThread", String.valueOf(controlledByThread));

        if (clearEachIteration) {
            clearCache(controlledByThread);
        }

        LOG.log(Level.FINE,
                "HTTPCacheManagerExecutor [{0}]: configured (clearEachIteration={1}, useExpires={2}, maxSize={3})",
                new Object[]{node.getTestName(), clearEachIteration, useExpires, maxSize});
    }

    /**
     * Looks up a cache entry for the given URL.
     *
     * @param url               the request URL
     * @param controlledByThread whether to use per-thread cache
     * @return the cache entry, or {@code null} if not cached
     */
    public static CacheEntry lookup(String url, boolean controlledByThread) {
        ConcurrentHashMap<String, CacheEntry> cache = controlledByThread
                ? THREAD_CACHE.get() : GLOBAL_CACHE;
        return cache.get(url);
    }

    /**
     * Stores a cache entry for the given URL.
     *
     * @param url                the request URL
     * @param entry              the cache entry to store
     * @param maxSize            maximum cache size
     * @param controlledByThread whether to use per-thread cache
     */
    public static void store(String url, CacheEntry entry, int maxSize,
                              boolean controlledByThread) {
        ConcurrentHashMap<String, CacheEntry> cache = controlledByThread
                ? THREAD_CACHE.get() : GLOBAL_CACHE;

        // Evict if at capacity (simple: clear oldest half)
        if (cache.size() >= maxSize) {
            int toRemove = maxSize / 2;
            var it = cache.keySet().iterator();
            for (int i = 0; i < toRemove && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
        cache.put(url, entry);
    }

    /**
     * Clears the cache.
     *
     * @param controlledByThread whether to clear per-thread or global cache
     */
    public static void clearCache(boolean controlledByThread) {
        if (controlledByThread) {
            THREAD_CACHE.get().clear();
        } else {
            GLOBAL_CACHE.clear();
        }
    }

    /**
     * Resets all caches. Intended for testing only.
     */
    static void resetAll() {
        GLOBAL_CACHE.clear();
        THREAD_CACHE.remove();
    }

    // -------------------------------------------------------------------------
    // CacheEntry record
    // -------------------------------------------------------------------------

    /**
     * Immutable cache entry storing response metadata for conditional requests.
     *
     * @param url          the cached URL
     * @param etag         the ETag header value, or {@code null}
     * @param lastModified the Last-Modified header value, or {@code null}
     * @param expiresAt    expiry timestamp (epoch millis); 0 means no expiry
     */
    public record CacheEntry(
            String url,
            String etag,
            String lastModified,
            long expiresAt
    ) {
        /**
         * Returns {@code true} if this entry has expired.
         */
        public boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }

        /**
         * Returns {@code true} if conditional request headers should be added.
         */
        public boolean hasConditionalHeaders() {
            return (etag != null && !etag.isEmpty())
                    || (lastModified != null && !lastModified.isEmpty());
        }
    }
}
