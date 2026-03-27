package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AssertionExecutor}.
 */
class AssertionExecutorTest {

    // -------------------------------------------------------------------------
    // Substring (test_type = 16) — default
    // -------------------------------------------------------------------------

    @Test
    void substringMatch_passes() {
        PlanNode node = assertionNode("Assertion.response_data", 16,
                List.of("hello"));

        SampleResult result = sampleWithBody("hello world");
        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("", result.getFailureMessage());
    }

    @Test
    void substringMatch_fails() {
        PlanNode node = assertionNode("Assertion.response_data", 16,
                List.of("not present"));

        SampleResult result = sampleWithBody("hello world");
        AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("Assertion"));
    }

    // -------------------------------------------------------------------------
    // Equals (test_type = 8)
    // -------------------------------------------------------------------------

    @Test
    void equalsMatch_passes() {
        PlanNode node = assertionNode("Assertion.response_data", 8, List.of("exact body"));

        SampleResult result = sampleWithBody("exact body");
        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    @Test
    void equalsMatch_fails_onPartialMatch() {
        PlanNode node = assertionNode("Assertion.response_data", 8, List.of("exact"));

        SampleResult result = sampleWithBody("exact body");
        AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Contains / regex find (test_type = 2)
    // -------------------------------------------------------------------------

    @Test
    void regexContains_passes() {
        PlanNode node = assertionNode("Assertion.response_data", 2, List.of("\"userId\":\\s*\\d+"));

        SampleResult result = sampleWithBody("{\"userId\": 42}");
        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    @Test
    void regexContains_fails() {
        PlanNode node = assertionNode("Assertion.response_data", 2, List.of("^OK$"));

        SampleResult result = sampleWithBody("NOT OK");
        AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Response code assertion
    // -------------------------------------------------------------------------

    @Test
    void responseCode_passes() {
        PlanNode node = assertionNode("Assertion.response_code", 8, List.of("200"));

        SampleResult result = new SampleResult("test");
        result.setStatusCode(200);
        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    @Test
    void responseCode_fails() {
        PlanNode node = assertionNode("Assertion.response_code", 8, List.of("200"));

        SampleResult result = new SampleResult("test");
        result.setStatusCode(404);
        AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Variable substitution in pattern
    // -------------------------------------------------------------------------

    @Test
    void patternWithVariableSubstitution() {
        PlanNode node = assertionNode("Assertion.response_data", 16, List.of("${expectedToken}"));

        SampleResult result = sampleWithBody("token:abc123");
        Map<String, String> vars = Map.of("expectedToken", "abc123");
        AssertionExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // assumeSuccess resets prior failure
    // -------------------------------------------------------------------------

    @Test
    void assumeSuccess_resetsPriorFailure() {
        PlanNode node = PlanNode.builder("ResponseAssertion", "reset")
                .property("Assertion.test_field", "Assertion.response_data")
                .property("Assertion.test_type",  16)
                .property("Assertion.assume_success", true)
                .property("Asserion.test_strings", List.of("world"))
                .build();

        SampleResult result = sampleWithBody("hello world");
        result.setFailureMessage("prior failure");

        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // No patterns — no-op
    // -------------------------------------------------------------------------

    @Test
    void noPatterns_noOp() {
        PlanNode node = PlanNode.builder("ResponseAssertion", "empty")
                .property("Assertion.test_field", "Assertion.response_data")
                .property("Assertion.test_type",  16)
                .build();

        SampleResult result = sampleWithBody("anything");
        AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Null guards
    // -------------------------------------------------------------------------

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                AssertionExecutor.execute(null, new SampleResult("x"), Map.of()));
    }

    @Test
    void nullResult_throws() {
        PlanNode node = assertionNode("Assertion.response_data", 16, List.of("x"));
        assertThrows(NullPointerException.class, () ->
                AssertionExecutor.execute(node, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode assertionNode(String field, int testType, List<String> patterns) {
        return PlanNode.builder("ResponseAssertion", "assertion")
                .property("Assertion.test_field",   field)
                .property("Assertion.test_type",    testType)
                .property("Asserion.test_strings",  new java.util.ArrayList<>(patterns))
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("test-sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
