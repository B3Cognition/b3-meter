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

import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;
import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpSamplerExecutor}.
 *
 * <p>Uses a recording stub {@link HttpClientFactory} so tests run without any
 * network infrastructure (engine-service has no WireMock dependency).
 */
class HttpSamplerExecutorTest {

    // =========================================================================
    // Basic execution
    // =========================================================================

    @Test
    void execute_returnsResult_with200() {
        RecordingHttpClient client = new RecordingHttpClient(
                new HttpResponse(200, "HTTP/1.1", Map.of(),
                        "Hello".getBytes(StandardCharsets.UTF_8),
                        5L, 10L, 15L, false));

        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = httpNode("example.com", "/", "GET");
        SampleResult result = exec.execute(node, Map.of());

        assertTrue(result.isSuccess(), "200 should be success");
        assertEquals(200, result.getStatusCode());
        assertEquals("Hello", result.getResponseBody());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_marksFailed_on404() {
        RecordingHttpClient client = new RecordingHttpClient(
                new HttpResponse(404, "HTTP/1.1", Map.of(), new byte[0],
                        0L, 5L, 5L, false));

        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);
        PlanNode node = httpNode("example.com", "/missing", "GET");
        SampleResult result = exec.execute(node, Map.of());

        assertFalse(result.isSuccess(), "404 should be marked as failure");
        assertEquals(404, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("404"));
    }

    @Test
    void execute_recordsTimingFromResponse() {
        RecordingHttpClient client = new RecordingHttpClient(
                new HttpResponse(200, "HTTP/1.1", Map.of(), new byte[0],
                        10L, 20L, 100L, false));

        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);
        PlanNode node = httpNode("example.com", "/", "GET");
        SampleResult result = exec.execute(node, Map.of());

        assertEquals(10L, result.getConnectTimeMs());
        assertEquals(20L, result.getLatencyMs());
    }

    // =========================================================================
    // URL construction
    // =========================================================================

    @Test
    void execute_buildsCorrectUrl_http80() {
        RecordingHttpClient client = new RecordingHttpClient(ok());
        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "myserver")
                .property("HTTPSampler.path",     "/api/users")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        exec.execute(node, Map.of());

        assertEquals("http://myserver/api/users", client.lastRequest().url());
    }

    @Test
    void execute_buildsCorrectUrl_nonDefaultPort() {
        RecordingHttpClient client = new RecordingHttpClient(ok());
        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "myserver")
                .property("HTTPSampler.path",     "/health")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .property("HTTPSampler.port",     8080)
                .build();

        exec.execute(node, Map.of());

        assertEquals("http://myserver:8080/health", client.lastRequest().url());
    }

    @Test
    void execute_buildsCorrectUrl_https443() {
        RecordingHttpClient client = new RecordingHttpClient(ok());
        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "secure.example.com")
                .property("HTTPSampler.path",     "/api")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "https")
                .build();

        exec.execute(node, Map.of());

        assertEquals("https://secure.example.com/api", client.lastRequest().url());
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_substitutesVariablesInDomain() {
        RecordingHttpClient client = new RecordingHttpClient(ok());
        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "${targetHost}")
                .property("HTTPSampler.path",     "/")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        Map<String, String> vars = Map.of("targetHost", "dynamic.example.com");
        exec.execute(node, vars);

        assertTrue(client.lastRequest().url().contains("dynamic.example.com"),
                "URL should contain resolved domain");
    }

    @Test
    void execute_substitutesVariablesInPath() {
        RecordingHttpClient client = new RecordingHttpClient(ok());
        HttpSamplerExecutor exec = new HttpSamplerExecutor(client);

        PlanNode node = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "example.com")
                .property("HTTPSampler.path",     "/users/${userId}")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        Map<String, String> vars = Map.of("userId", "99");
        exec.execute(node, vars);

        assertTrue(client.lastRequest().url().endsWith("/users/99"));
    }

    // =========================================================================
    // Network error handling
    // =========================================================================

    @Test
    void execute_marksFailure_onIOException() {
        HttpClientFactory failingClient = new HttpClientFactory() {
            @Override
            public HttpResponse execute(HttpRequest request) throws IOException {
                throw new IOException("connection refused");
            }

            @Override
            public void close() {}
        };

        HttpSamplerExecutor exec = new HttpSamplerExecutor(failingClient);
        PlanNode node = httpNode("localhost", "/", "GET");
        SampleResult result = exec.execute(node, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("connection refused"));
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void nullHttpClient_throws() {
        assertThrows(NullPointerException.class, () -> new HttpSamplerExecutor(null));
    }

    @Test
    void nullNode_throws() {
        HttpSamplerExecutor exec = new HttpSamplerExecutor(StubInterpreterFactory.noOpHttpClient());
        assertThrows(NullPointerException.class, () -> exec.execute(null, Map.of()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode httpNode(String domain, String path, String method) {
        return PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   domain)
                .property("HTTPSampler.path",     path)
                .property("HTTPSampler.method",   method)
                .property("HTTPSampler.protocol", "http")
                .build();
    }

    private static HttpResponse ok() {
        return new HttpResponse(200, "HTTP/1.1", Map.of(), new byte[0],
                0L, 1L, 1L, false);
    }

    // =========================================================================
    // Recording stub
    // =========================================================================

    private static final class RecordingHttpClient implements HttpClientFactory {

        private final HttpResponse fixedResponse;
        private final List<HttpRequest> requests = new ArrayList<>();

        RecordingHttpClient(HttpResponse fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            requests.add(request);
            return fixedResponse;
        }

        @Override
        public void close() {}

        HttpRequest lastRequest() {
            assertFalse(requests.isEmpty(), "No requests were made");
            return requests.get(requests.size() - 1);
        }
    }
}
