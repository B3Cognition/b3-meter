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
 * Tests for {@link CssExtractorExecutor}.
 */
class CssExtractorExecutorTest {

    private static final String HTML_BODY = "<html><body>"
            + "<div class=\"content\" id=\"main\"><p>Hello</p><p>World</p></div>"
            + "<div class=\"sidebar\"><a href=\"/link1\">Link 1</a></div>"
            + "<span class=\"tag\">Tag1</span>"
            + "<span class=\"tag\">Tag2</span>"
            + "</body></html>";

    @Test
    void extractsByTag() {
        PlanNode node = cssExtractorNode("SPAN", "span", "", "NOT_FOUND", "1");
        SampleResult result = sampleWithBody(HTML_BODY);
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertEquals("Tag1", vars.get("SPAN"));
    }

    @Test
    void extractsByTagAndClass() {
        PlanNode node = cssExtractorNode("VAL", "span.tag", "", "NOT_FOUND", "2");
        SampleResult result = sampleWithBody(HTML_BODY);
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertEquals("Tag2", vars.get("VAL"));
    }

    @Test
    void extractsAttribute() {
        PlanNode node = cssExtractorNode("LINK", "a", "href", "NONE", "1");
        SampleResult result = sampleWithBody(HTML_BODY);
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertEquals("/link1", vars.get("LINK"));
    }

    @Test
    void noMatch_returnsDefault() {
        PlanNode node = cssExtractorNode("X", "table", "", "DEFAULT", "1");
        SampleResult result = sampleWithBody(HTML_BODY);
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertEquals("DEFAULT", vars.get("X"));
    }

    @Test
    void emptyBody_returnsDefault() {
        PlanNode node = cssExtractorNode("X", "div", "", "FALLBACK", "1");
        SampleResult result = sampleWithBody("");
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertEquals("FALLBACK", vars.get("X"));
    }

    @Test
    void extractById() {
        PlanNode node = cssExtractorNode("MAIN", "div#main", "", "NONE", "1");
        SampleResult result = sampleWithBody(HTML_BODY);
        Map<String, String> vars = new HashMap<>();
        CssExtractorExecutor.execute(node, result, vars);
        assertNotNull(vars.get("MAIN"));
        assertNotEquals("NONE", vars.get("MAIN"));
    }

    @Test
    void buildTagPattern_tagOnly() {
        var pattern = CssExtractorExecutor.buildTagPattern("div");
        assertTrue(pattern.matcher("<div>content</div>").find());
        assertFalse(pattern.matcher("<span>content</span>").find());
    }

    @Test
    void extractTextContent_stripsInnerTags() {
        String text = CssExtractorExecutor.extractTextContent("<p>Hello <b>World</b></p>");
        assertEquals("Hello World", text);
    }

    @Test
    void extractAttribute_findsValue() {
        String val = CssExtractorExecutor.extractAttribute("<a href=\"/test\" class=\"link\">X</a>", "href");
        assertEquals("/test", val);
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                CssExtractorExecutor.execute(null, new SampleResult("x"), new HashMap<>()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode cssExtractorNode(String refName, String expr, String attribute,
                                              String defaultVal, String matchNumber) {
        return PlanNode.builder("HtmlExtractor", "css-" + refName)
                .property("HtmlExtractor.refname", refName)
                .property("HtmlExtractor.expr", expr)
                .property("HtmlExtractor.attribute", attribute)
                .property("HtmlExtractor.default", defaultVal)
                .property("HtmlExtractor.match_number", matchNumber)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
