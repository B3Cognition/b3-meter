package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XPath2AssertionExecutor}.
 */
class XPath2AssertionExecutorTest {

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("xpath2");
        assertThrows(NullPointerException.class,
                () -> XPath2AssertionExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = xpathNode("/root");
        assertThrows(NullPointerException.class,
                () -> XPath2AssertionExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = xpathNode("/root");
        SampleResult r = new SampleResult("xpath2");
        assertThrows(NullPointerException.class,
                () -> XPath2AssertionExecutor.execute(node, r, null));
    }

    @Test
    void passesMatchingXPath() {
        PlanNode node = xpathNode("/root/item");
        SampleResult result = new SampleResult("xpath2-pass");
        result.setResponseBody("<root><item>hello</item></root>");

        XPath2AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    @Test
    void failsNonMatchingXPath() {
        PlanNode node = xpathNode("/root/nonexistent");
        SampleResult result = new SampleResult("xpath2-fail");
        result.setResponseBody("<root><item>hello</item></root>");

        XPath2AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FAILED"));
    }

    @Test
    void negatedAssertionInverts() {
        PlanNode node = PlanNode.builder("XPath2Assertion", "xpath2-negate")
                .property("XPath.xpath", "/root/nonexistent")
                .property("XPath.negate", true)
                .build();

        SampleResult result = new SampleResult("xpath2-negate");
        result.setResponseBody("<root><item>hello</item></root>");

        XPath2AssertionExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
    }

    @Test
    void handlesEmptyBody() {
        PlanNode node = xpathNode("/root");
        SampleResult result = new SampleResult("xpath2-empty");
        result.setResponseBody("");

        XPath2AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    @Test
    void handlesEmptyXPathExpression() {
        PlanNode node = xpathNode("");
        SampleResult result = new SampleResult("xpath2-no-expr");
        result.setResponseBody("<root/>");

        XPath2AssertionExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("empty"));
    }

    private static PlanNode xpathNode(String xpath) {
        return PlanNode.builder("XPath2Assertion", "xpath2-test")
                .property("XPath.xpath", xpath)
                .build();
    }
}
