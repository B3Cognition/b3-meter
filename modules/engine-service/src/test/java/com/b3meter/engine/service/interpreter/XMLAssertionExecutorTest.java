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
 * Tests for {@link XMLAssertionExecutor}.
 */
class XMLAssertionExecutorTest {

    @Test
    void wellFormedXml_passes() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("<root><child>text</child></root>");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void malformedXml_fails() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("<root><unclosed>");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("not well-formed XML"));
    }

    @Test
    void emptyBody_fails() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("empty"));
    }

    @Test
    void plainText_fails() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("This is not XML");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void jsonBody_fails() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("{\"key\": \"value\"}");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void xmlWithAttributes_passes() {
        PlanNode node = xmlAssertionNode();
        SampleResult result = sampleWithBody("<book id=\"1\" title=\"Test\"><chapter>One</chapter></book>");
        XMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                XMLAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode xmlAssertionNode() {
        return PlanNode.builder("XMLAssertion", "xml-assertion").build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
