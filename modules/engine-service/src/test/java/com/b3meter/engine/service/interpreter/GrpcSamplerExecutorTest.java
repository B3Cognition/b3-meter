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
 * Tests for {@link GrpcSamplerExecutor}.
 *
 * <p>Uses a recording stub {@link HttpClientFactory} to verify request construction
 * and response handling without a live gRPC server.
 */
class GrpcSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("grpc-test");
        assertThrows(NullPointerException.class,
                () -> GrpcSamplerExecutor.execute(null, result, Map.of(),
                        StubInterpreterFactory.noOpHttpClient()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = grpcNode("localhost", 443, "com.example.Greeter", "SayHello", "{}");
        assertThrows(NullPointerException.class,
                () -> GrpcSamplerExecutor.execute(node, null, Map.of(),
                        StubInterpreterFactory.noOpHttpClient()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = grpcNode("localhost", 443, "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-test");
        assertThrows(NullPointerException.class,
                () -> GrpcSamplerExecutor.execute(node, result, null,
                        StubInterpreterFactory.noOpHttpClient()));
    }

    @Test
    void execute_throwsOnNullHttpClient() {
        PlanNode node = grpcNode("localhost", 443, "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-test");
        assertThrows(NullPointerException.class,
                () -> GrpcSamplerExecutor.execute(node, result, Map.of(), null));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyHost() {
        PlanNode node = grpcNode("", 443, "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-test");

        GrpcSamplerExecutor.execute(node, result, Map.of(),
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("host is empty"));
    }

    @Test
    void execute_failsOnEmptyService() {
        PlanNode node = grpcNode("localhost", 443, "", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-test");

        GrpcSamplerExecutor.execute(node, result, Map.of(),
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("service"));
    }

    @Test
    void execute_failsOnEmptyMethod() {
        PlanNode node = grpcNode("localhost", 443, "com.example.Greeter", "", "{}");
        SampleResult result = new SampleResult("grpc-test");

        GrpcSamplerExecutor.execute(node, result, Map.of(),
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("method"));
    }

    // =========================================================================
    // Request construction
    // =========================================================================

    @Test
    void execute_buildsCorrectUrl_withTls() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = grpcNode("grpc.example.com", 443,
                "com.example.Greeter", "SayHello", "{\"name\":\"World\"}");
        SampleResult result = new SampleResult("grpc-url");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertEquals("https://grpc.example.com/com.example.Greeter/SayHello",
                client.lastRequest().url());
    }

    @Test
    void execute_buildsCorrectUrl_withoutTls() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = PlanNode.builder("GrpcSampler", "grpc-notls")
                .property("GrpcSampler.host", "localhost")
                .property("GrpcSampler.port", 50051)
                .property("GrpcSampler.service", "mypackage.MyService")
                .property("GrpcSampler.method", "DoWork")
                .property("GrpcSampler.requestBody", "{}")
                .property("GrpcSampler.useTls", false)
                .build();
        SampleResult result = new SampleResult("grpc-notls");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertEquals("http://localhost:50051/mypackage.MyService/DoWork",
                client.lastRequest().url());
    }

    @Test
    void execute_setsGrpcHeaders() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-headers");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        HttpRequest req = client.lastRequest();
        assertEquals("application/grpc", req.headers().get("Content-Type"));
        assertEquals("trailers", req.headers().get("TE"));
    }

    @Test
    void execute_usesHttp2Protocol() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-h2");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertEquals(HttpRequest.PROTOCOL_HTTP_2, client.lastRequest().protocol());
    }

    @Test
    void execute_sendsLengthPrefixedBody() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{\"name\":\"test\"}");
        SampleResult result = new SampleResult("grpc-body");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        byte[] body = client.lastRequest().body();
        assertNotNull(body);
        assertTrue(body.length > 5, "Body should have 5-byte header + payload");

        // First byte: compression flag
        assertEquals(0x00, body[0], "Compression flag should be 0 (none)");

        // Next 4 bytes: message length (big-endian)
        int messageLength = ((body[1] & 0xFF) << 24)
                | ((body[2] & 0xFF) << 16)
                | ((body[3] & 0xFF) << 8)
                | (body[4] & 0xFF);
        byte[] jsonBytes = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals(jsonBytes.length, messageLength, "Length prefix should match JSON size");

        // Payload should be the JSON
        String payload = new String(body, 5, body.length - 5, StandardCharsets.UTF_8);
        assertEquals("{\"name\":\"test\"}", payload);
    }

    // =========================================================================
    // Response handling
    // =========================================================================

    @Test
    void execute_returnsSuccess_onGrpcOK() {
        String responseJson = "{\"message\":\"Hello World\"}";
        RecordingHttpClient client = new RecordingHttpClient(grpcOk(responseJson));
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-ok");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getStatusCode(), "gRPC status 0 = OK");
        assertTrue(result.getResponseBody().contains("Hello World"));
    }

    @Test
    void execute_returnsFailure_onGrpcError() {
        Map<String, String> headers = new HashMap<>();
        headers.put("grpc-status", "5"); // NOT_FOUND
        HttpResponse response = new HttpResponse(200, "HTTP/2", headers,
                buildTestGrpcFrame("{\"error\":\"not found\"}"),
                0L, 1L, 1L, false);

        RecordingHttpClient client = new RecordingHttpClient(response);
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-notfound");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertFalse(result.isSuccess());
        assertEquals(5, result.getStatusCode(), "gRPC status should be 5 (NOT_FOUND)");
    }

    @Test
    void execute_mapsHttpStatus_whenNoGrpcHeader() {
        HttpResponse response = new HttpResponse(404, "HTTP/2", Map.of(),
                new byte[0], 0L, 1L, 1L, false);

        RecordingHttpClient client = new RecordingHttpClient(response);
        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-404");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertFalse(result.isSuccess());
        assertEquals(5, result.getStatusCode(),
                "HTTP 404 should map to gRPC NOT_FOUND (5)");
    }

    // =========================================================================
    // Network error handling
    // =========================================================================

    @Test
    void execute_handlesIOException() {
        HttpClientFactory failingClient = new HttpClientFactory() {
            @Override
            public HttpResponse execute(HttpRequest request) throws IOException {
                throw new IOException("connection refused");
            }

            @Override
            public void close() {}
        };

        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-error");

        GrpcSamplerExecutor.execute(node, result, Map.of(), failingClient);

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("connection refused"));
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariables() {
        RecordingHttpClient client = new RecordingHttpClient(grpcOk("{}"));
        PlanNode node = PlanNode.builder("GrpcSampler", "grpc-vars")
                .property("GrpcSampler.host", "${grpcHost}")
                .property("GrpcSampler.port", 443)
                .property("GrpcSampler.service", "${svc}")
                .property("GrpcSampler.method", "${mtd}")
                .property("GrpcSampler.requestBody", "{\"id\":\"${userId}\"}")
                .property("GrpcSampler.useTls", true)
                .build();
        SampleResult result = new SampleResult("grpc-vars");
        Map<String, String> vars = Map.of(
                "grpcHost", "api.example.com",
                "svc", "com.example.UserService",
                "mtd", "GetUser",
                "userId", "42"
        );

        GrpcSamplerExecutor.execute(node, result, vars, client);

        HttpRequest req = client.lastRequest();
        assertEquals("https://api.example.com/com.example.UserService/GetUser", req.url());

        // Verify body contains resolved variable
        String bodyStr = new String(req.body(), 5, req.body().length - 5, StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("\"42\""), "Body should contain resolved userId");
    }

    // =========================================================================
    // gRPC frame encoding/decoding
    // =========================================================================

    @Test
    void buildGrpcFrame_encodesCorrectly() {
        byte[] frame = GrpcSamplerExecutor.buildGrpcFrame("{\"key\":\"value\"}");

        assertEquals(0x00, frame[0], "Compression flag should be 0");
        int length = ((frame[1] & 0xFF) << 24)
                | ((frame[2] & 0xFF) << 16)
                | ((frame[3] & 0xFF) << 8)
                | (frame[4] & 0xFF);
        assertEquals("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8).length, length);
    }

    @Test
    void buildGrpcFrame_emptyBody() {
        byte[] frame = GrpcSamplerExecutor.buildGrpcFrame("");

        assertEquals(5, frame.length, "Empty body should still have 5-byte header");
        assertEquals(0x00, frame[0]);
        assertEquals(0, frame[1] | frame[2] | frame[3] | frame[4],
                "Length should be 0 for empty body");
    }

    @Test
    void decodeGrpcResponse_decodesCorrectly() {
        String original = "{\"message\":\"hello\"}";
        byte[] frame = GrpcSamplerExecutor.buildGrpcFrame(original);

        String decoded = GrpcSamplerExecutor.decodeGrpcResponse(frame);
        assertEquals(original, decoded);
    }

    @Test
    void decodeGrpcResponse_handlesShortBody() {
        String decoded = GrpcSamplerExecutor.decodeGrpcResponse(new byte[]{0x01, 0x02});
        assertEquals(new String(new byte[]{0x01, 0x02}, StandardCharsets.UTF_8), decoded);
    }

    @Test
    void decodeGrpcResponse_handlesNull() {
        String decoded = GrpcSamplerExecutor.decodeGrpcResponse(null);
        assertEquals("", decoded);
    }

    // =========================================================================
    // gRPC status extraction
    // =========================================================================

    @Test
    void extractGrpcStatus_fromHeader() {
        HttpResponse response = new HttpResponse(200, "HTTP/2",
                Map.of("grpc-status", "0"), new byte[0], 0L, 0L, 0L, false);
        assertEquals(0, GrpcSamplerExecutor.extractGrpcStatus(response));
    }

    @Test
    void extractGrpcStatus_fromHeader_nonZero() {
        Map<String, String> headers = new HashMap<>();
        headers.put("grpc-status", "14");
        HttpResponse response = new HttpResponse(200, "HTTP/2",
                headers, new byte[0], 0L, 0L, 0L, false);
        assertEquals(14, GrpcSamplerExecutor.extractGrpcStatus(response));
    }

    @Test
    void extractGrpcStatus_mapsHttp200() {
        HttpResponse response = new HttpResponse(200, "HTTP/2",
                Map.of(), new byte[0], 0L, 0L, 0L, false);
        assertEquals(0, GrpcSamplerExecutor.extractGrpcStatus(response));
    }

    @Test
    void extractGrpcStatus_mapsHttp500() {
        HttpResponse response = new HttpResponse(500, "HTTP/2",
                Map.of(), new byte[0], 0L, 0L, 0L, false);
        assertEquals(13, GrpcSamplerExecutor.extractGrpcStatus(response));
    }

    @Test
    void extractGrpcStatus_mapsHttp503() {
        HttpResponse response = new HttpResponse(503, "HTTP/2",
                Map.of(), new byte[0], 0L, 0L, 0L, false);
        assertEquals(14, GrpcSamplerExecutor.extractGrpcStatus(response));
    }

    @Test
    void extractGrpcStatus_mapsUnknownHttp() {
        HttpResponse response = new HttpResponse(418, "HTTP/2",
                Map.of(), new byte[0], 0L, 0L, 0L, false);
        assertEquals(2, GrpcSamplerExecutor.extractGrpcStatus(response),
                "Unknown HTTP status should map to gRPC UNKNOWN (2)");
    }

    // =========================================================================
    // Timing
    // =========================================================================

    @Test
    void execute_recordsTiming() {
        HttpResponse response = new HttpResponse(200, "HTTP/2",
                Map.of("grpc-status", "0"),
                GrpcSamplerExecutor.buildGrpcFrame("{}"),
                10L, 20L, 30L, false);
        RecordingHttpClient client = new RecordingHttpClient(response);

        PlanNode node = grpcNode("localhost", 443,
                "com.example.Greeter", "SayHello", "{}");
        SampleResult result = new SampleResult("grpc-timing");

        GrpcSamplerExecutor.execute(node, result, Map.of(), client);

        assertEquals(10L, result.getConnectTimeMs());
        assertEquals(20L, result.getLatencyMs());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode grpcNode(String host, int port, String service,
                                     String method, String body) {
        return PlanNode.builder("GrpcSampler", "grpc-test")
                .property("GrpcSampler.host", host)
                .property("GrpcSampler.port", port)
                .property("GrpcSampler.service", service)
                .property("GrpcSampler.method", method)
                .property("GrpcSampler.requestBody", body)
                .property("GrpcSampler.useTls", true)
                .build();
    }

    private static HttpResponse grpcOk(String responseJson) {
        Map<String, String> headers = new HashMap<>();
        headers.put("grpc-status", "0");
        return new HttpResponse(200, "HTTP/2", headers,
                buildTestGrpcFrame(responseJson),
                0L, 1L, 1L, false);
    }

    private static byte[] buildTestGrpcFrame(String json) {
        return GrpcSamplerExecutor.buildGrpcFrame(json);
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
