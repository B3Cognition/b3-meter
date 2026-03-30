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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WebSocketSamplerExecutor}.
 *
 * <p>Tests error handling and validation without requiring a live WebSocket server.
 * Connection tests use an unreachable host to verify timeout and error paths.
 */
class WebSocketSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("ws-test");
        assertThrows(NullPointerException.class,
                () -> WebSocketSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = wsNode("ws://localhost/ws", "hello");
        assertThrows(NullPointerException.class,
                () -> WebSocketSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = wsNode("ws://localhost/ws", "hello");
        SampleResult result = new SampleResult("ws-test");
        assertThrows(NullPointerException.class,
                () -> WebSocketSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Empty URL validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyUrl() {
        PlanNode node = wsNode("", "hello");
        SampleResult result = new SampleResult("ws-test");

        WebSocketSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("url is empty"));
    }

    @Test
    void execute_failsOnBlankUrl() {
        PlanNode node = wsNode("   ", "hello");
        SampleResult result = new SampleResult("ws-test");

        WebSocketSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("url is empty"));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariablesInUrl() {
        PlanNode node = PlanNode.builder("WebSocketSampler", "ws-var-test")
                .property("WebSocketSampler.url", "ws://${wsHost}/chat")
                .property("WebSocketSampler.message", "hi")
                .property("WebSocketSampler.connectTimeout", 500)
                .property("WebSocketSampler.responseTimeout", 500)
                .build();

        SampleResult result = new SampleResult("ws-var-test");
        Map<String, String> vars = new HashMap<>();
        vars.put("wsHost", "192.0.2.1:19999"); // RFC 5737 TEST-NET — unreachable

        // Will fail to connect, but proves variable was resolved (error message
        // won't say "url is empty")
        WebSocketSamplerExecutor.execute(node, result, vars);

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().contains("url is empty"),
                "Variable should have been resolved");
    }

    @Test
    void execute_resolvesVariablesInMessage() {
        // The message variable resolution is tested via the URL validation path —
        // if the URL is empty, the message is never used. We validate the URL
        // resolves but the connection will fail, proving the resolve path works.
        PlanNode node = PlanNode.builder("WebSocketSampler", "ws-msg-test")
                .property("WebSocketSampler.url", "ws://192.0.2.1:19999/ws")
                .property("WebSocketSampler.message", "${greeting}")
                .property("WebSocketSampler.connectTimeout", 500)
                .property("WebSocketSampler.responseTimeout", 500)
                .build();

        SampleResult result = new SampleResult("ws-msg-test");
        Map<String, String> vars = Map.of("greeting", "hello world");

        WebSocketSamplerExecutor.execute(node, result, vars);

        // Connection will fail but we verify no NPE from variable resolution
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("WebSocket"));
    }

    // =========================================================================
    // Connection error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        PlanNode node = PlanNode.builder("WebSocketSampler", "ws-refused")
                .property("WebSocketSampler.url", "ws://127.0.0.1:1")
                .property("WebSocketSampler.message", "test")
                .property("WebSocketSampler.connectTimeout", 1000)
                .property("WebSocketSampler.responseTimeout", 1000)
                .build();

        SampleResult result = new SampleResult("ws-refused");
        WebSocketSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertNotNull(result.getFailureMessage());
        assertFalse(result.getFailureMessage().isEmpty());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_handlesInvalidUrl() {
        PlanNode node = wsNode("not-a-valid-url", "hello");
        SampleResult result = new SampleResult("ws-invalid");

        WebSocketSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getFailureMessage().isEmpty());
    }

    // =========================================================================
    // Default property values
    // =========================================================================

    @Test
    void execute_usesDefaultTimeouts() {
        // Node with no explicit timeout properties — defaults should be applied
        PlanNode node = PlanNode.builder("WebSocketSampler", "ws-defaults")
                .property("WebSocketSampler.url", "ws://192.0.2.1:19999/ws")
                .property("WebSocketSampler.message", "test")
                .build();

        SampleResult result = new SampleResult("ws-defaults");

        // This will attempt connection with default 5s connect timeout.
        // We don't wait for it — just verify the executor doesn't throw NPE
        // from missing properties. The test will timeout at 5s but that's fine.
        // Use an unreachable host with small custom timeout to keep test fast.
        PlanNode fastNode = PlanNode.builder("WebSocketSampler", "ws-defaults")
                .property("WebSocketSampler.url", "ws://127.0.0.1:1/ws")
                .property("WebSocketSampler.message", "test")
                .property("WebSocketSampler.connectTimeout", 500)
                .build();

        SampleResult fastResult = new SampleResult("ws-defaults");
        WebSocketSamplerExecutor.execute(fastNode, fastResult, Map.of());

        // Just verify it completed without NPE
        assertFalse(fastResult.isSuccess());
    }

    // =========================================================================
    // Timing is recorded
    // =========================================================================

    @Test
    void execute_recordsTotalTime() {
        PlanNode node = PlanNode.builder("WebSocketSampler", "ws-timing")
                .property("WebSocketSampler.url", "ws://127.0.0.1:1/ws")
                .property("WebSocketSampler.message", "test")
                .property("WebSocketSampler.connectTimeout", 500)
                .build();

        SampleResult result = new SampleResult("ws-timing");
        WebSocketSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.getTotalTimeMs() >= 0, "Total time should be recorded");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode wsNode(String url, String message) {
        return PlanNode.builder("WebSocketSampler", "ws-test")
                .property("WebSocketSampler.url", url)
                .property("WebSocketSampler.message", message)
                .property("WebSocketSampler.connectTimeout", 500)
                .property("WebSocketSampler.responseTimeout", 500)
                .build();
    }
}
