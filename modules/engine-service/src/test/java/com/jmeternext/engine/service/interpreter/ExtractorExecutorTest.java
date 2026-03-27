package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ExtractorExecutor}.
 */
class ExtractorExecutorTest {

    // =========================================================================
    // RegexExtractor
    // =========================================================================

    @Test
    void regex_extractsFirstGroup() {
        PlanNode node = regexNode("TOKEN", "\"token\":\\s*\"([^\"]+)\"", "$1$", 1, "NOT_FOUND");

        SampleResult result = sampleWithBody("{\"token\": \"abc123\", \"user\": \"bob\"}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("abc123", vars.get("TOKEN"));
    }

    @Test
    void regex_defaultOnNoMatch() {
        PlanNode node = regexNode("TOKEN", "nomatch(\\w+)", "$1$", 1, "DEFAULT_VAL");

        SampleResult result = sampleWithBody("hello world");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("DEFAULT_VAL", vars.get("TOKEN"));
    }

    @Test
    void regex_wholeMatchTemplate() {
        PlanNode node = regexNode("FULL", "hello \\w+", "$0$", 1, "");

        SampleResult result = sampleWithBody("prefix hello world suffix");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("hello world", vars.get("FULL"));
    }

    @Test
    void regex_multipleGroupsInTemplate() {
        PlanNode node = regexNode("PAIR", "(\\w+):(\\d+)", "$1$=$2$", 1, "");

        SampleResult result = sampleWithBody("id:42");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("id=42", vars.get("PAIR"));
    }

    @Test
    void regex_secondMatch() {
        PlanNode node = regexNode("ID", "id=(\\d+)", "$1$", 2, "NONE");

        SampleResult result = sampleWithBody("id=10&id=20&id=30");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("20", vars.get("ID"));
    }

    // =========================================================================
    // JSONPathExtractor
    // =========================================================================

    @Test
    void json_extractsSimpleField() {
        PlanNode node = jsonNode("USER_ID", "$.id", "NOT_FOUND");

        SampleResult result = sampleWithBody("{\"id\": 42, \"name\": \"Alice\"}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("42", vars.get("USER_ID"));
    }

    @Test
    void json_extractsStringField() {
        PlanNode node = jsonNode("USERNAME", "$.name", "UNKNOWN");

        SampleResult result = sampleWithBody("{\"id\": 1, \"name\": \"Bob\"}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("Bob", vars.get("USERNAME"));
    }

    @Test
    void json_nestedPath() {
        PlanNode node = jsonNode("CITY", "$.address.city", "N/A");

        SampleResult result = sampleWithBody("{\"address\": {\"city\": \"London\", \"zip\": \"EC1\"}}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("London", vars.get("CITY"));
    }

    @Test
    void json_defaultOnMissingKey() {
        PlanNode node = jsonNode("MISSING", "$.noSuchKey", "DEFAULT");

        SampleResult result = sampleWithBody("{\"id\": 1}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("DEFAULT", vars.get("MISSING"));
    }

    @Test
    void json_arrayElement() {
        PlanNode node = jsonNode("FIRST", "$.items[0]", "NONE");

        SampleResult result = sampleWithBody("{\"items\": [\"alpha\", \"beta\", \"gamma\"]}");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("alpha", vars.get("FIRST"));
    }

    @Test
    void json_defaultOnEmptyBody() {
        PlanNode node = jsonNode("X", "$.id", "FALLBACK");

        SampleResult result = sampleWithBody("");
        Map<String, String> vars = new HashMap<>();

        ExtractorExecutor.execute(node, result, vars);

        assertEquals("FALLBACK", vars.get("X"));
    }

    // =========================================================================
    // extractJsonPath unit tests (package-visible static method)
    // =========================================================================

    @Test
    void extractJsonPath_simpleField() {
        String json = "{\"status\": \"ok\"}";
        assertEquals("ok", ExtractorExecutor.extractJsonPath(json, "$.status", "FAIL"));
    }

    @Test
    void extractJsonPath_numericValue() {
        String json = "{\"count\": 7}";
        assertEquals("7", ExtractorExecutor.extractJsonPath(json, "$.count", "0"));
    }

    @Test
    void extractJsonPath_missingKey() {
        String json = "{\"a\": 1}";
        assertEquals("DEFAULT", ExtractorExecutor.extractJsonPath(json, "$.b", "DEFAULT"));
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                ExtractorExecutor.execute(null, new SampleResult("x"), new HashMap<>()));
    }

    @Test
    void nullResult_throws() {
        PlanNode node = regexNode("X", ".*", "$0$", 1, "");
        assertThrows(NullPointerException.class, () ->
                ExtractorExecutor.execute(node, null, new HashMap<>()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode regexNode(String refName, String regex, String template,
                                       int matchNumber, String defaultVal) {
        return PlanNode.builder("RegexExtractor", "regex-" + refName)
                .property("RegexExtractor.refname",      refName)
                .property("RegexExtractor.regex",        regex)
                .property("RegexExtractor.template",     template)
                .property("RegexExtractor.match_number", matchNumber)
                .property("RegexExtractor.default",      defaultVal)
                .build();
    }

    private static PlanNode jsonNode(String refName, String jsonPath, String defaultVal) {
        return PlanNode.builder("JSONPathExtractor", "json-" + refName)
                .property("JSONPathExtractor.referenceNames", refName)
                .property("JSONPathExtractor.jsonPathExprs",  jsonPath)
                .property("JSONPathExtractor.defaultValues",  defaultVal)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
