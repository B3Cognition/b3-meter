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
 * Tests for {@link XPathAssertionExecutor}.
 */
class XPathAssertionExecutorTest {

    private static final String XML_BODY = "<root><item id=\"1\">Hello</item><item id=\"2\">World</item></root>";

    @Test
    void xpath_matchesNode_passes() {
        PlanNode node = xpathAssertionNode("/root/item", false);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void xpath_noMatch_fails() {
        PlanNode node = xpathAssertionNode("/root/missing", false);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FAILED"),
                "Failure message should indicate assertion failed: " + result.getFailureMessage());
    }

    @Test
    void xpath_negate_flips() {
        PlanNode node = xpathAssertionNode("/root/missing", true);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        // Negated: no match becomes pass
        assertTrue(result.isSuccess());
    }

    @Test
    void xpath_negate_existingNode_fails() {
        PlanNode node = xpathAssertionNode("/root/item", true);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void xpath_booleanExpression_passes() {
        PlanNode node = xpathAssertionNode("count(/root/item) = 2", false);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void xpath_booleanExpression_fails() {
        PlanNode node = xpathAssertionNode("count(/root/item) = 5", false);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void xpath_emptyBody_fails() {
        PlanNode node = xpathAssertionNode("/root", false);
        SampleResult result = sampleWithBody("");
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    @Test
    void xpath_malformedXml_fails() {
        PlanNode node = xpathAssertionNode("/root", false);
        SampleResult result = sampleWithBody("<root><unclosed>");
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("XML/XPath error"));
    }

    @Test
    void xpath_emptyExpression_fails() {
        PlanNode node = xpathAssertionNode("", false);
        SampleResult result = sampleWithBody(XML_BODY);
        XPathAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("empty"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                XPathAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode xpathAssertionNode(String xpath, boolean negate) {
        return PlanNode.builder("XPathAssertion", "xpath-assertion")
                .property("XPath.xpath", xpath)
                .property("XPath.negate", negate)
                .property("XPath.namespace", false)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
