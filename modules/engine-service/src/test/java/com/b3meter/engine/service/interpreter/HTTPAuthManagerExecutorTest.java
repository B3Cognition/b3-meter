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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HTTPAuthManagerExecutor}.
 */
class HTTPAuthManagerExecutorTest {

    @Test
    void configure_storesAuthEntriesInVariables() {
        PlanNode authEntry = PlanNode.builder("Authorization", "")
                .property("Authorization.url", "http://example.com")
                .property("Authorization.username", "admin")
                .property("Authorization.password", "secret")
                .property("Authorization.domain", "")
                .property("Authorization.realm", "")
                .property("Authorization.mechanism", "BASIC")
                .build();

        PlanNode node = PlanNode.builder("AuthManager", "Auth Manager")
                .property("AuthManager.auth_list", List.of(authEntry))
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPAuthManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_auth_enabled"));
        assertEquals("1", variables.get("__jmn_auth_count"));
        assertEquals("http://example.com", variables.get("__jmn_auth_0_url"));
        assertEquals("admin", variables.get("__jmn_auth_0_username"));
        assertEquals("secret", variables.get("__jmn_auth_0_password"));
        assertEquals("BASIC", variables.get("__jmn_auth_0_mechanism"));
    }

    @Test
    void getAuthorizationHeader_basicAuth_returnsCorrectHeader() {
        PlanNode authEntry = PlanNode.builder("Authorization", "")
                .property("Authorization.url", "http://api.example.com")
                .property("Authorization.username", "user")
                .property("Authorization.password", "pass")
                .property("Authorization.mechanism", "BASIC")
                .build();

        PlanNode node = PlanNode.builder("AuthManager", "Auth")
                .property("AuthManager.auth_list", List.of(authEntry))
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPAuthManagerExecutor.configure(node, variables);

        String header = HTTPAuthManagerExecutor.getAuthorizationHeader(
                "http://api.example.com/users", variables);

        assertNotNull(header);
        assertTrue(header.startsWith("Basic "));

        String expected = Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expected, header);
    }

    @Test
    void getAuthorizationHeader_noMatch_returnsNull() {
        PlanNode authEntry = PlanNode.builder("Authorization", "")
                .property("Authorization.url", "http://other.com")
                .property("Authorization.username", "user")
                .property("Authorization.password", "pass")
                .property("Authorization.mechanism", "BASIC")
                .build();

        PlanNode node = PlanNode.builder("AuthManager", "Auth")
                .property("AuthManager.auth_list", List.of(authEntry))
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPAuthManagerExecutor.configure(node, variables);

        String header = HTTPAuthManagerExecutor.getAuthorizationHeader(
                "http://notmatching.com/page", variables);
        assertNull(header);
    }

    @Test
    void getAuthorizationHeader_notConfigured_returnsNull() {
        Map<String, String> variables = new HashMap<>();
        assertNull(HTTPAuthManagerExecutor.getAuthorizationHeader(
                "http://example.com", variables));
    }

    @Test
    void getAuthorizationHeader_multipleEntries_matchesFirst() {
        PlanNode entry1 = PlanNode.builder("Authorization", "")
                .property("Authorization.url", "http://api.com/admin")
                .property("Authorization.username", "admin")
                .property("Authorization.password", "adminpass")
                .property("Authorization.mechanism", "BASIC")
                .build();

        PlanNode entry2 = PlanNode.builder("Authorization", "")
                .property("Authorization.url", "http://api.com")
                .property("Authorization.username", "user")
                .property("Authorization.password", "userpass")
                .property("Authorization.mechanism", "BASIC")
                .build();

        PlanNode node = PlanNode.builder("AuthManager", "Auth")
                .property("AuthManager.auth_list", List.of(entry1, entry2))
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPAuthManagerExecutor.configure(node, variables);

        // Should match entry1 (more specific)
        String header = HTTPAuthManagerExecutor.getAuthorizationHeader(
                "http://api.com/admin/users", variables);
        assertNotNull(header);

        String expected = Base64.getEncoder()
                .encodeToString("admin:adminpass".getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + expected, header);
    }

    @Test
    void urlMatches_prefixMatching() {
        assertTrue(HTTPAuthManagerExecutor.urlMatches(
                "http://example.com/path", "http://example.com"));
        assertFalse(HTTPAuthManagerExecutor.urlMatches(
                "http://other.com/path", "http://example.com"));
        assertFalse(HTTPAuthManagerExecutor.urlMatches("", "http://example.com"));
        assertFalse(HTTPAuthManagerExecutor.urlMatches("http://example.com", ""));
        assertFalse(HTTPAuthManagerExecutor.urlMatches(null, "pattern"));
        assertFalse(HTTPAuthManagerExecutor.urlMatches("url", null));
    }

    @Test
    void configure_emptyAuthList() {
        PlanNode node = PlanNode.builder("AuthManager", "Empty Auth")
                .build();

        Map<String, String> variables = new HashMap<>();
        HTTPAuthManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_auth_enabled"));
        assertEquals("0", variables.get("__jmn_auth_count"));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> HTTPAuthManagerExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("AuthManager", "test").build();
        assertThrows(NullPointerException.class,
                () -> HTTPAuthManagerExecutor.configure(node, null));
    }
}
