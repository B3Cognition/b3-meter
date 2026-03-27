package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HTMLLinkParserExecutor}.
 */
class HTMLLinkParserExecutorTest {

    @Test
    void extractsLinks() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody(
                "<html><body>" +
                "<a href=\"/page1\">Page 1</a>" +
                "<a href=\"/page2\">Page 2</a>" +
                "</body></html>");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);

        assertEquals("2", vars.get("__jmeter_link_count"));
        assertEquals("/page1", vars.get("__jmeter_link_1"));
        assertEquals("/page2", vars.get("__jmeter_link_2"));
    }

    @Test
    void extractsForms() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody(
                "<html><body>" +
                "<form action=\"/submit\"><input type='text'/></form>" +
                "<form action=\"/login\"><input type='text'/></form>" +
                "</body></html>");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);

        assertEquals("2", vars.get("__jmeter_form_count"));
        assertEquals("/submit", vars.get("__jmeter_form_1"));
        assertEquals("/login", vars.get("__jmeter_form_2"));
    }

    @Test
    void extractsLinksAndForms() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody(
                "<html><body>" +
                "<a href=\"https://example.com\">Link</a>" +
                "<form action=\"/api/submit\"><button>Submit</button></form>" +
                "</body></html>");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);

        assertEquals("1", vars.get("__jmeter_link_count"));
        assertEquals("https://example.com", vars.get("__jmeter_link_1"));
        assertEquals("1", vars.get("__jmeter_form_count"));
        assertEquals("/api/submit", vars.get("__jmeter_form_1"));
    }

    @Test
    void noLinksOrForms_zeroCounts() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody("<html><body><p>No links here</p></body></html>");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);

        assertEquals("0", vars.get("__jmeter_link_count"));
        assertEquals("0", vars.get("__jmeter_form_count"));
    }

    @Test
    void nullLastResult_noOp() {
        PlanNode node = linkParserNode();
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, null, vars);
        assertNull(vars.get("__jmeter_link_count"));
    }

    @Test
    void emptyBody_noOp() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody("");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);
        assertNull(vars.get("__jmeter_link_count"));
    }

    @Test
    void singleQuotedHref_extracted() {
        PlanNode node = linkParserNode();
        SampleResult lastResult = sampleWithBody("<a href='/single'>Test</a>");
        Map<String, String> vars = new HashMap<>();
        HTMLLinkParserExecutor.execute(node, lastResult, vars);
        assertEquals("1", vars.get("__jmeter_link_count"));
        assertEquals("/single", vars.get("__jmeter_link_1"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                HTMLLinkParserExecutor.execute(null, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode linkParserNode() {
        return PlanNode.builder("HTMLLinkParser", "link-parser").build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
