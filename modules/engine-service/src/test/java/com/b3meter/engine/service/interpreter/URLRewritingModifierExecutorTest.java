package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link URLRewritingModifierExecutor}.
 */
class URLRewritingModifierExecutorTest {

    @Test
    void extractsJsessionid() {
        PlanNode node = urlRewriteNode("jsessionid");
        SampleResult lastResult = sampleWithBody(
                "<a href=\"/app;jsessionid=ABC123DEF\">Next</a>");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertEquals("ABC123DEF", vars.get("jsessionid"));
    }

    @Test
    void extractsPhpSessionId() {
        PlanNode node = urlRewriteNode("PHPSESSID");
        SampleResult lastResult = sampleWithBody(
                "<a href=\"/page?PHPSESSID=xyz789&other=1\">Link</a>");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertEquals("xyz789", vars.get("PHPSESSID"));
    }

    @Test
    void extractsFromFormAction() {
        PlanNode node = urlRewriteNode("sid");
        SampleResult lastResult = sampleWithBody(
                "<form action=\"/submit?sid=SESS12345\"><input type='submit'/></form>");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertEquals("SESS12345", vars.get("sid"));
    }

    @Test
    void notFound_noVariable() {
        PlanNode node = urlRewriteNode("jsessionid");
        SampleResult lastResult = sampleWithBody("<html><body>No session</body></html>");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertNull(vars.get("jsessionid"));
    }

    @Test
    void nullLastResult_noOp() {
        PlanNode node = urlRewriteNode("jsessionid");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, null, vars);
        assertNull(vars.get("jsessionid"));
    }

    @Test
    void emptyArgument_skips() {
        PlanNode node = PlanNode.builder("HTTPURLRewritingModifier", "url-rewrite")
                .property("argument", "")
                .build();
        SampleResult lastResult = sampleWithBody("jsessionid=ABC");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertTrue(vars.isEmpty());
    }

    @Test
    void caseInsensitiveExtraction() {
        PlanNode node = urlRewriteNode("JSESSIONID");
        SampleResult lastResult = sampleWithBody(
                "<a href=\"/app?JSESSIONID=CaseSensTest\">Link</a>");
        Map<String, String> vars = new HashMap<>();
        URLRewritingModifierExecutor.execute(node, lastResult, vars);
        assertEquals("CaseSensTest", vars.get("JSESSIONID"));
    }

    @Test
    void extractSessionId_internal() {
        String body = "some text jsessionid=ABC123 more text";
        assertEquals("ABC123", URLRewritingModifierExecutor.extractSessionId(body, "jsessionid"));
    }

    @Test
    void extractSessionId_notFound() {
        String body = "no session here";
        assertNull(URLRewritingModifierExecutor.extractSessionId(body, "jsessionid"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                URLRewritingModifierExecutor.execute(null, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode urlRewriteNode(String argument) {
        return PlanNode.builder("HTTPURLRewritingModifier", "url-rewrite")
                .property("argument", argument)
                .property("path_extension", false)
                .property("path_extension_no_equals", false)
                .property("path_extension_no_questionmark", false)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
