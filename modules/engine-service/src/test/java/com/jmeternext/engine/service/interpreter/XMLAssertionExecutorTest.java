package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
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
