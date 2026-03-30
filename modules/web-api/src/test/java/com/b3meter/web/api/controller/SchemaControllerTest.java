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
package com.b3meter.web.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SchemaController}.
 *
 * <p>Boots the full Spring context on a random port and exercises both endpoints
 * via {@link TestRestTemplate} to verify that cached schemas are returned
 * correctly and that unknown component names yield 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings({"rawtypes", "unchecked"})
class SchemaControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // GET /api/v1/schemas — list all
    // -------------------------------------------------------------------------

    @Test
    void listAll_returns200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/schemas", List.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void listAll_bodyIsNotNull() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/schemas", List.class);
        assertNotNull(response.getBody(), "response body must not be null");
    }

    @Test
    void listAll_containsThreadGroupSchema() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/schemas", List.class);
        assertNotNull(response.getBody());
        boolean found = response.getBody().stream()
                .anyMatch(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> schema = (Map<String, Object>) item;
                    return "ThreadGroup".equals(schema.get("componentName"));
                });
        assertTrue(found, "ThreadGroup schema must be present in list");
    }

    @Test
    void listAll_containsHttpSamplerSchema() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/schemas", List.class);
        assertNotNull(response.getBody());
        boolean found = response.getBody().stream()
                .anyMatch(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> schema = (Map<String, Object>) item;
                    return "HTTPSampler".equals(schema.get("componentName"));
                });
        assertTrue(found, "HTTPSampler schema must be present in list");
    }

    @Test
    void listAll_hasAtLeastFiveSchemas() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                "/api/v1/schemas", List.class);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5,
                "Expected at least 5 schemas but got " + response.getBody().size());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/schemas/{componentName} — single
    // -------------------------------------------------------------------------

    @Test
    void getByName_threadGroup_returns200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getByName_threadGroup_hasCorrectComponentName() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertNotNull(response.getBody());
        assertEquals("ThreadGroup", response.getBody().get("componentName"));
    }

    @Test
    void getByName_threadGroup_hasCategoryThread() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertNotNull(response.getBody());
        assertEquals("thread", response.getBody().get("componentCategory"));
    }

    @Test
    void getByName_threadGroup_hasNumThreadsProperty() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> properties =
                (List<Map<String, Object>>) response.getBody().get("properties");
        assertNotNull(properties, "properties must be present");

        boolean hasNumThreads = properties.stream()
                .anyMatch(p -> "num_threads".equals(p.get("name")));
        assertTrue(hasNumThreads, "ThreadGroup schema must include num_threads property");
    }

    @Test
    void getByName_threadGroup_numThreadsTypeIsInteger() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> properties =
                (List<Map<String, Object>>) response.getBody().get("properties");
        assertNotNull(properties);

        String type = properties.stream()
                .filter(p -> "num_threads".equals(p.get("name")))
                .map(p -> (String) p.get("type"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("num_threads not found"));

        assertEquals("integer", type);
    }

    @Test
    void getByName_threadGroup_onSampleErrorIsEnum() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> properties =
                (List<Map<String, Object>>) response.getBody().get("properties");
        assertNotNull(properties);

        Map<String, Object> onSampleError = properties.stream()
                .filter(p -> "on_sample_error".equals(p.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("on_sample_error not found"));

        assertEquals("enum", onSampleError.get("type"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) onSampleError.get("enumValues");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("CONTINUE"));
        assertTrue(enumValues.contains("STOP_TEST"));
    }

    @Test
    void getByName_httpSampler_methodIsEnum() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/HTTPSampler", Map.class);
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> properties =
                (List<Map<String, Object>>) response.getBody().get("properties");
        assertNotNull(properties);

        Map<String, Object> method = properties.stream()
                .filter(p -> "method".equals(p.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("method not found"));

        assertEquals("enum", method.get("type"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) method.get("enumValues");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("GET"));
        assertTrue(enumValues.contains("POST"));
        assertTrue(enumValues.contains("DELETE"));
    }

    @Test
    void getByName_unknownComponent_returns404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/schemas/NonExistentComponent", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getByName_schemasAreCached_consistentResults() {
        // Call twice — should return identical data (proves caching works at least
        // in the sense that the registry is stable across calls)
        ResponseEntity<Map> first = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);
        ResponseEntity<Map> second = restTemplate.getForEntity(
                "/api/v1/schemas/ThreadGroup", Map.class);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(
                first.getBody().get("componentName"),
                second.getBody().get("componentName"),
                "Schema componentName must be the same across repeated calls"
        );
        assertEquals(
                first.getBody().get("componentCategory"),
                second.getBody().get("componentCategory"),
                "Schema componentCategory must be the same across repeated calls"
        );
    }
}
