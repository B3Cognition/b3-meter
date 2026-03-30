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
 * Tests for {@link CompareAssertionExecutor}.
 */
class CompareAssertionExecutorTest {

    @Test
    void matchingContent_passes() {
        PlanNode node = compareNode(true, 0, false, "response body", 0);
        SampleResult result = sampleWithBody("response body");
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void mismatchedContent_fails() {
        PlanNode node = compareNode(true, 0, false, "expected content", 0);
        SampleResult result = sampleWithBody("actual content");
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Content mismatch"));
    }

    @Test
    void contentComparisonDisabled_alwaysPasses() {
        PlanNode node = compareNode(false, 0, false, "different", 0);
        SampleResult result = sampleWithBody("actual");
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void timeWithinThreshold_passes() {
        PlanNode node = compareNode(false, 100, false, "", 200);
        SampleResult result = sampleWithBody("body");
        result.setTotalTimeMs(250); // diff = 50, within 100ms threshold
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void timeExceedsThreshold_fails() {
        PlanNode node = compareNode(false, 50, false, "", 200);
        SampleResult result = sampleWithBody("body");
        result.setTotalTimeMs(300); // diff = 100, exceeds 50ms threshold
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Time difference"));
    }

    @Test
    void emptyExpectedContent_passes() {
        PlanNode node = compareNode(true, 0, false, "", 0);
        SampleResult result = sampleWithBody("anything");
        CompareAssertionExecutor.execute(node, result, new HashMap<>());
        assertTrue(result.isSuccess());
    }

    @Test
    void variableResolution_works() {
        PlanNode node = PlanNode.builder("CompareAssertion", "compare")
                .property("CompareAssertion.compareContent", true)
                .property("CompareAssertion.compareTime", 0)
                .property("CompareAssertion.compareHeaders", false)
                .property("CompareAssertion.expectedContent", "${expected}")
                .property("CompareAssertion.expectedTime", 0)
                .build();
        SampleResult result = sampleWithBody("hello world");
        Map<String, String> vars = new HashMap<>();
        vars.put("expected", "hello world");
        CompareAssertionExecutor.execute(node, result, vars);
        assertTrue(result.isSuccess());
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                CompareAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode compareNode(boolean compareContent, int compareTime,
                                         boolean compareHeaders, String expectedContent,
                                         int expectedTime) {
        return PlanNode.builder("CompareAssertion", "compare-assertion")
                .property("CompareAssertion.compareContent", compareContent)
                .property("CompareAssertion.compareTime", compareTime)
                .property("CompareAssertion.compareHeaders", compareHeaders)
                .property("CompareAssertion.expectedContent", expectedContent)
                .property("CompareAssertion.expectedTime", expectedTime)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
