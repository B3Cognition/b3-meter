package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HTMLAssertionExecutor}.
 */
class HTMLAssertionExecutorTest {

    @Test
    void wellFormedHtml_passes() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("<html><body><p>Hello</p></body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void unclosedTag_fails() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("<html><body><p>Hello</body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Mismatched tag"),
                "Expected mismatch error, got: " + result.getFailureMessage());
    }

    @Test
    void mismatchedTags_fails() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("<div><span></div></span>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Mismatched tag"));
    }

    @Test
    void selfClosingTags_pass() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("<html><body><br/><img src='x'/><hr/></body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void voidElements_pass() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("<html><head><meta charset='utf-8'><link rel='stylesheet'></head><body><br><img src='x'><input type='text'></body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void errorThreshold_allowsErrors() {
        PlanNode node = htmlAssertionNode(2, 0);
        SampleResult result = sampleWithBody("<div><p>unclosed<span>also unclosed");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        // 3 unclosed tags (div, p, span) — exceeds threshold of 2
        assertFalse(result.isSuccess());
    }

    @Test
    void errorThreshold_withinLimit_passes() {
        PlanNode node = htmlAssertionNode(5, 5);
        SampleResult result = sampleWithBody("<div><p>unclosed");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        // 2 unclosed tags — within threshold of 5
        assertTrue(result.isSuccess());
    }

    @Test
    void emptyBody_fails() {
        PlanNode node = htmlAssertionNode(0, 0);
        SampleResult result = sampleWithBody("");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("empty"));
    }

    @Test
    void doctypeCheck_missingDoctype_warns() {
        PlanNode node = PlanNode.builder("HTMLAssertion", "html-assertion")
                .property("HTMLAssertion.ERROR_THRESHOLD", 0)
                .property("HTMLAssertion.WARNING_THRESHOLD", 0)
                .property("HTMLAssertion.DOCTYPE", "html")
                .property("HTMLAssertion.FORMAT", "html")
                .build();
        SampleResult result = sampleWithBody("<html><body>test</body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("DOCTYPE"));
    }

    @Test
    void doctypePresent_passes() {
        PlanNode node = PlanNode.builder("HTMLAssertion", "html-assertion")
                .property("HTMLAssertion.ERROR_THRESHOLD", 0)
                .property("HTMLAssertion.WARNING_THRESHOLD", 0)
                .property("HTMLAssertion.DOCTYPE", "html")
                .property("HTMLAssertion.FORMAT", "html")
                .build();
        SampleResult result = sampleWithBody("<!DOCTYPE html><html><body>test</body></html>");
        HTMLAssertionExecutor.execute(node, result, Map.of());
        assertTrue(result.isSuccess());
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                HTMLAssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode htmlAssertionNode(int errorThreshold, int warningThreshold) {
        return PlanNode.builder("HTMLAssertion", "html-assertion")
                .property("HTMLAssertion.ERROR_THRESHOLD", errorThreshold)
                .property("HTMLAssertion.WARNING_THRESHOLD", warningThreshold)
                .property("HTMLAssertion.DOCTYPE", "")
                .property("HTMLAssertion.FORMAT", "html")
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
