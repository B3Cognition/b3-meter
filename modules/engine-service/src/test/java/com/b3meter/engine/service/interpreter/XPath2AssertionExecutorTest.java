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
