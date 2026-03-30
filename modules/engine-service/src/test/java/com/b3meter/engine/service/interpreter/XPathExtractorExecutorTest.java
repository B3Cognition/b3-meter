package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XPathExtractorExecutor}.
 */
class XPathExtractorExecutorTest {

    private static final String XML_BODY =
            "<root><item id=\"1\">Alpha</item><item id=\"2\">Beta</item><item id=\"3\">Gamma</item></root>";

    @Test
    void extractsTextContent() {
        PlanNode node = xpathExtractorNode("ITEM", "/root/item[1]/text()", "NOT_FOUND", "1");
        SampleResult result = sampleWithBody(XML_BODY);
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("Alpha", vars.get("ITEM"));
    }

    @Test
    void extractsAttribute() {
        PlanNode node = xpathExtractorNode("ID", "/root/item[2]/@id", "NOT_FOUND", "1");
        SampleResult result = sampleWithBody(XML_BODY);
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("2", vars.get("ID"));
    }

    @Test
    void noMatch_returnsDefault() {
        PlanNode node = xpathExtractorNode("MISSING", "/root/nothing", "DEFAULT", "1");
        SampleResult result = sampleWithBody(XML_BODY);
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("DEFAULT", vars.get("MISSING"));
    }

    @Test
    void matchAll_storesIndexed() {
        PlanNode node = xpathExtractorNode("ITEMS", "/root/item", "NONE", "-1");
        SampleResult result = sampleWithBody(XML_BODY);
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("Alpha", vars.get("ITEMS"));
        assertEquals("Alpha", vars.get("ITEMS_1"));
        assertEquals("Beta", vars.get("ITEMS_2"));
        assertEquals("Gamma", vars.get("ITEMS_3"));
        assertEquals("3", vars.get("ITEMS_matchNr"));
    }

    @Test
    void emptyBody_returnsDefault() {
        PlanNode node = xpathExtractorNode("X", "/root", "FALLBACK", "1");
        SampleResult result = sampleWithBody("");
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("FALLBACK", vars.get("X"));
    }

    @Test
    void malformedXml_returnsDefault() {
        PlanNode node = xpathExtractorNode("X", "/root", "DEF", "1");
        SampleResult result = sampleWithBody("<broken><xml");
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("DEF", vars.get("X"));
    }

    @Test
    void secondMatch() {
        PlanNode node = xpathExtractorNode("ITEM", "/root/item", "NONE", "2");
        SampleResult result = sampleWithBody(XML_BODY);
        Map<String, String> vars = new HashMap<>();
        XPathExtractorExecutor.execute(node, result, vars);
        assertEquals("Beta", vars.get("ITEM"));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                XPathExtractorExecutor.execute(null, new SampleResult("x"), new HashMap<>()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode xpathExtractorNode(String refName, String xpath,
                                                String defaultVal, String matchNumber) {
        return PlanNode.builder("XPathExtractor", "xpath-" + refName)
                .property("XPathExtractor.refname", refName)
                .property("XPathExtractor.xpathQuery", xpath)
                .property("XPathExtractor.default", defaultVal)
                .property("XPathExtractor.matchNumber", matchNumber)
                .property("XPathExtractor.namespace", false)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
