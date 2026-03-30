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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HTTPCacheManagerExecutor}.
 */
class HTTPCacheManagerExecutorTest {

    @AfterEach
    void cleanup() {
        HTTPCacheManagerExecutor.resetAll();
    }

    @Test
    void configure_setsVariablesInMap() {
        PlanNode node = PlanNode.builder("CacheManager", "HTTP Cache Manager")
                .property("clearEachIteration", false)
                .property("useExpires", true)
                .property("maxSize", 3000)
                .property("controlledByThread", true)
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPCacheManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_cache_enabled"));
        assertEquals("false", variables.get("__jmn_cache_clearEachIteration"));
        assertEquals("true", variables.get("__jmn_cache_useExpires"));
        assertEquals("3000", variables.get("__jmn_cache_maxSize"));
        assertEquals("true", variables.get("__jmn_cache_controlledByThread"));
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("CacheManager", "Cache").build();

        Map<String, String> variables = new HashMap<>();
        HTTPCacheManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_cache_enabled"));
        assertEquals("false", variables.get("__jmn_cache_clearEachIteration"));
        assertEquals("true", variables.get("__jmn_cache_useExpires"));
        assertEquals("5000", variables.get("__jmn_cache_maxSize"));
    }

    @Test
    void storeAndLookup_globalMode() {
        String url = "https://example.com/resource";
        var entry = new HTTPCacheManagerExecutor.CacheEntry(
                url, "\"etag123\"", "Tue, 01 Jan 2030 00:00:00 GMT",
                System.currentTimeMillis() + 60000);

        HTTPCacheManagerExecutor.store(url, entry, 5000, false);

        var found = HTTPCacheManagerExecutor.lookup(url, false);
        assertNotNull(found);
        assertEquals("\"etag123\"", found.etag());
        assertFalse(found.isExpired());
    }

    @Test
    void storeAndLookup_perThreadMode() {
        String url = "https://example.com/page";
        var entry = new HTTPCacheManagerExecutor.CacheEntry(
                url, null, "Mon, 01 Jan 2030 00:00:00 GMT", 0);

        HTTPCacheManagerExecutor.store(url, entry, 5000, true);

        var found = HTTPCacheManagerExecutor.lookup(url, true);
        assertNotNull(found);
        assertEquals("Mon, 01 Jan 2030 00:00:00 GMT", found.lastModified());
    }

    @Test
    void lookup_nonexistent_returnsNull() {
        assertNull(HTTPCacheManagerExecutor.lookup("https://noexist.com", false));
        assertNull(HTTPCacheManagerExecutor.lookup("https://noexist.com", true));
    }

    @Test
    void cacheEntry_isExpired_whenPastExpiry() {
        var expired = new HTTPCacheManagerExecutor.CacheEntry(
                "url", "etag", null, System.currentTimeMillis() - 1000);
        assertTrue(expired.isExpired());
    }

    @Test
    void cacheEntry_notExpired_whenZeroExpiry() {
        var noExpiry = new HTTPCacheManagerExecutor.CacheEntry(
                "url", "etag", null, 0);
        assertFalse(noExpiry.isExpired(), "Zero expiry means no expiration");
    }

    @Test
    void cacheEntry_hasConditionalHeaders() {
        var withEtag = new HTTPCacheManagerExecutor.CacheEntry("url", "etag", null, 0);
        assertTrue(withEtag.hasConditionalHeaders());

        var withLastMod = new HTTPCacheManagerExecutor.CacheEntry("url", null, "date", 0);
        assertTrue(withLastMod.hasConditionalHeaders());

        var neither = new HTTPCacheManagerExecutor.CacheEntry("url", null, null, 0);
        assertFalse(neither.hasConditionalHeaders());
    }

    @Test
    void clearCache_removesEntries() {
        String url = "https://example.com/cached";
        var entry = new HTTPCacheManagerExecutor.CacheEntry(url, "etag", null, 0);

        HTTPCacheManagerExecutor.store(url, entry, 5000, false);
        assertNotNull(HTTPCacheManagerExecutor.lookup(url, false));

        HTTPCacheManagerExecutor.clearCache(false);
        assertNull(HTTPCacheManagerExecutor.lookup(url, false));
    }

    @Test
    void configure_clearEachIteration_clearsCache() {
        // Pre-populate cache
        String url = "https://example.com/old";
        HTTPCacheManagerExecutor.store(url,
                new HTTPCacheManagerExecutor.CacheEntry(url, "etag", null, 0),
                5000, false);

        PlanNode node = PlanNode.builder("CacheManager", "Cache")
                .property("clearEachIteration", true)
                .build();

        HTTPCacheManagerExecutor.configure(node, new HashMap<>());

        assertNull(HTTPCacheManagerExecutor.lookup(url, false),
                "Cache should be cleared when clearEachIteration is true");
    }

    @Test
    void store_evictsWhenAtCapacity() {
        // Fill cache to max
        for (int i = 0; i < 10; i++) {
            HTTPCacheManagerExecutor.store("url" + i,
                    new HTTPCacheManagerExecutor.CacheEntry("url" + i, null, null, 0),
                    10, false);
        }

        // Store one more — should trigger eviction
        HTTPCacheManagerExecutor.store("urlNew",
                new HTTPCacheManagerExecutor.CacheEntry("urlNew", null, null, 0),
                10, false);

        assertNotNull(HTTPCacheManagerExecutor.lookup("urlNew", false),
                "New entry should be stored");
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> HTTPCacheManagerExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("CacheManager", "test").build();
        assertThrows(NullPointerException.class,
                () -> HTTPCacheManagerExecutor.configure(node, null));
    }
}
