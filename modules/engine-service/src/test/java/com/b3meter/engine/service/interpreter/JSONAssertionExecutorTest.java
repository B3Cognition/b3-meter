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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JSONAssertionExecutor}.
 */
class JSONAssertionExecutorTest {

    @Test
    void jsonPath_exists_passes() {
        PlanNode node = jsonAssertionNode("$.status", "", false, false, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void jsonPath_valueMatch_passes() {
        PlanNode node = jsonAssertionNode("$.status", "ok", true, false, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void jsonPath_valueMatch_fails() {
        PlanNode node = jsonAssertionNode("$.status", "error", true, false, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Expected 'error'"));
    }

    @Test
    void jsonPath_notFound_fails() {
        PlanNode node = jsonAssertionNode("$.missing", "value", true, false, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("not found"));
    }

    @Test
    void jsonPath_expectNull_passes() {
        PlanNode node = jsonAssertionNode("$.missing", "", false, true, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void jsonPath_invert_flips() {
        PlanNode node = jsonAssertionNode("$.status", "wrong", true, false, true, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        // Inverted: mismatch becomes pass
        assertTrue(result.isSuccess());
    }

    @Test
    void jsonPath_regex_passes() {
        PlanNode node = jsonAssertionNode("$.status", "o[k]", true, false, false, true);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void jsonPath_regex_fails() {
        PlanNode node = jsonAssertionNode("$.status", "^error$", true, false, false, true);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void jsonPath_nested_passes() {
        PlanNode node = jsonAssertionNode("$.data.id", "42", true, false, false, false);
        SampleResult result = sampleWithBody("{\"data\": {\"id\": 42}}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void emptyBody_fails() {
        PlanNode node = jsonAssertionNode("$.status", "ok", true, false, false, false);
        SampleResult result = sampleWithBody("");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void emptyPath_fails() {
        PlanNode node = jsonAssertionNode("", "ok", true, false, false, false);
        SampleResult result = sampleWithBody("{\"status\": \"ok\"}");
        JSONAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                JSONAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode jsonAssertionNode(String jsonPath, String expectedValue,
                                              boolean jsonValidation, boolean expectNull,
                                              boolean invert, boolean isRegex) {
        return PlanNode.builder("JSONPathAssertion", "json-assertion")
                .property("JSON_PATH", jsonPath)
                .property("EXPECTED_VALUE", expectedValue)
                .property("JSONVALIDATION", jsonValidation)
                .property("EXPECT_NULL", expectNull)
                .property("INVERT", invert)
                .property("ISREGEX", isRegex)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
