package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SSESamplerExecutor}.
 *
 * <p>Uses an embedded {@link HttpServer} to simulate SSE endpoints. No external
 * libraries are required (Constitution Principle I).
 */
class SSESamplerExecutorTest {

    private HttpServer server;
    private int serverPort;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("sse-test");
        assertThrows(NullPointerException.class,
                () -> SSESamplerExecutor.execute(null, result, Map.of(), null));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = sseNode("http://localhost/events", 1000);
        assertThrows(NullPointerException.class,
                () -> SSESamplerExecutor.execute(node, null, Map.of(), null));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = sseNode("http://localhost/events", 1000);
        SampleResult result = new SampleResult("sse-test");
        assertThrows(NullPointerException.class,
                () -> SSESamplerExecutor.execute(node, result, null, null));
    }

    // =========================================================================
    // Empty URL validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyUrl() {
        PlanNode node = sseNode("", 1000);
        SampleResult result = new SampleResult("sse-test");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("url is empty"));
    }

    // =========================================================================
    // Successful SSE event collection
    // =========================================================================

    @Test
    void execute_collectsSSEEvents() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            String events = "data: event1\n\ndata: event2\n\ndata: event3\n\n";
            out.write(events.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        });
        server.start();

        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 2000);
        SampleResult result = new SampleResult("sse-collect");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        String body = result.getResponseBody();
        assertTrue(body.contains("event1"), "Should contain event1");
        assertTrue(body.contains("event2"), "Should contain event2");
        assertTrue(body.contains("event3"), "Should contain event3");
    }

    @Test
    void execute_collectsNamedEvents() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            String events = "event: update\ndata: first\n\n"
                    + "event: heartbeat\ndata: ping\n\n"
                    + "event: update\ndata: second\n\n";
            out.write(events.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        });
        server.start();

        PlanNode node = PlanNode.builder("SSESampler", "sse-named")
                .property("SSESampler.url", "http://127.0.0.1:" + serverPort + "/events")
                .property("SSESampler.duration", 2000)
                .property("SSESampler.eventName", "update")
                .build();
        SampleResult result = new SampleResult("sse-named");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertTrue(result.isSuccess());
        String body = result.getResponseBody();
        assertTrue(body.contains("first"), "Should contain first update");
        assertTrue(body.contains("second"), "Should contain second update");
        assertFalse(body.contains("ping"), "Should NOT contain heartbeat");
    }

    // =========================================================================
    // HTTP error status
    // =========================================================================

    @Test
    void execute_failsOnNon2xx() {
        server.createContext("/events", exchange -> {
            exchange.sendResponseHeaders(503, -1);
        });
        server.start();

        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 1000);
        SampleResult result = new SampleResult("sse-503");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertFalse(result.isSuccess());
        assertEquals(503, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("503"));
    }

    // =========================================================================
    // Connection error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        // Don't start the server — port is allocated but not listening
        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 1000);
        SampleResult result = new SampleResult("sse-refused");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariablesInUrl() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("data: ok\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        });
        server.start();

        PlanNode node = PlanNode.builder("SSESampler", "sse-var")
                .property("SSESampler.url", "http://127.0.0.1:${ssePort}/events")
                .property("SSESampler.duration", 2000)
                .build();
        SampleResult result = new SampleResult("sse-var");
        Map<String, String> vars = new HashMap<>();
        vars.put("ssePort", String.valueOf(serverPort));

        SSESamplerExecutor.execute(node, result, vars, null);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
    }

    // =========================================================================
    // Timing recorded
    // =========================================================================

    @Test
    void execute_recordsTiming() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write("data: test\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        });
        server.start();

        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 1000);
        SampleResult result = new SampleResult("sse-timing");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertTrue(result.getConnectTimeMs() >= 0, "Connect time should be recorded");
        assertTrue(result.getLatencyMs() >= 0, "Latency should be recorded");
        assertTrue(result.getTotalTimeMs() >= 0, "Total time should be recorded");
    }

    // =========================================================================
    // Empty stream
    // =========================================================================

    @Test
    void execute_handlesEmptyStream() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 1000);
        SampleResult result = new SampleResult("sse-empty");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertEquals("", result.getResponseBody());
    }

    // =========================================================================
    // Multiline data
    // =========================================================================

    @Test
    void execute_handlesMultilineData() {
        server.createContext("/events", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            String events = "data: line1\ndata: line2\n\n";
            out.write(events.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        });
        server.start();

        PlanNode node = sseNode("http://127.0.0.1:" + serverPort + "/events", 2000);
        SampleResult result = new SampleResult("sse-multiline");

        SSESamplerExecutor.execute(node, result, Map.of(), null);

        assertTrue(result.isSuccess());
        String body = result.getResponseBody();
        assertTrue(body.contains("line1"), "Should contain line1");
        assertTrue(body.contains("line2"), "Should contain line2");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode sseNode(String url, int durationMs) {
        return PlanNode.builder("SSESampler", "sse-test")
                .property("SSESampler.url", url)
                .property("SSESampler.duration", durationMs)
                .build();
    }
}
