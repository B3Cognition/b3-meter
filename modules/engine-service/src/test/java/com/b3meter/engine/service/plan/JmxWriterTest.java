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
package com.b3meter.engine.service.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JmxWriter}.
 *
 * <p>Focuses on correct XML output structure, XML entity escaping,
 * and the serialization of each supported property type.
 */
class JmxWriterTest {

    // =========================================================================
    // Basic output structure
    // =========================================================================

    @Test
    void write_producesXmlDeclaration() {
        PlanNode root = minimalPlan();
        String xml = JmxWriter.write(root);
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "Output must start with XML declaration");
    }

    @Test
    void write_jmeterTestPlanRoot_wrapsWithVersionAttributes() {
        PlanNode root = minimalPlan();
        String xml = JmxWriter.write(root);
        assertTrue(xml.contains("<jmeterTestPlan"), "Output must contain jmeterTestPlan element");
        assertTrue(xml.contains("version=\"1.2\""),  "Output must include version attribute");
        assertTrue(xml.contains("</jmeterTestPlan>"), "Output must close jmeterTestPlan");
    }

    @Test
    void write_containsTopLevelHashTree() {
        String xml = JmxWriter.write(minimalPlan());
        assertTrue(xml.contains("<hashTree>") || xml.contains("<hashTree/>"),
                "Output must contain a hashTree block");
    }

    @Test
    void write_threadGroupElementPresent() {
        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 10)
                .build();
        PlanNode plan = PlanNode.builder("TestPlan", "My Plan").child(tg).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(plan)
                .build();

        String xml = JmxWriter.write(root);
        assertTrue(xml.contains("<ThreadGroup"), "ThreadGroup element must be in output");
        assertTrue(xml.contains("testname=\"Users\""), "testname attribute must be in output");
        assertTrue(xml.contains("testclass=\"ThreadGroup\""), "testclass attribute must be in output");
    }

    // =========================================================================
    // Property type serialization
    // =========================================================================

    @Test
    void write_stringProp() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("key", "value").build());
        assertEquals("value", node.getStringProp("key"));
    }

    @Test
    void write_intProp() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("count", 42).build());
        assertEquals(42, node.getIntProp("count"));
    }

    @Test
    void write_longProp() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("big", 9999999999L).build());
        assertEquals(9999999999L, node.getLongProp("big"));
    }

    @Test
    void write_boolPropTrue() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("flag", true).build());
        assertTrue(node.getBoolProp("flag"));
    }

    @Test
    void write_boolPropFalse() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("flag", false).build());
        assertFalse(node.getBoolProp("flag"));
    }

    @Test
    void write_doubleProp() throws Exception {
        PlanNode node = roundTrip(PlanNode.builder("TestPlan", "P")
                .property("ratio", 3.14).build());
        assertEquals(3.14, node.getDoubleProp("ratio"), 0.001);
    }

    @Test
    void write_elementProp() throws Exception {
        PlanNode nested = PlanNode.builder("LoopController", "loop")
                .property("LoopController.loops", 5)
                .build();
        PlanNode node = roundTrip(PlanNode.builder("ThreadGroup", "TG")
                .property("ThreadGroup.main_controller", nested).build());

        PlanNode reparsedNested = node.getElementProp("ThreadGroup.main_controller");
        assertNotNull(reparsedNested, "elementProp must survive round-trip");
        assertEquals(5, reparsedNested.getIntProp("LoopController.loops"));
    }

    // =========================================================================
    // XML entity escaping
    // =========================================================================

    @Test
    void escapeXml_ampersand() {
        assertEquals("a&amp;b", JmxWriter.escapeXml("a&b"));
    }

    @Test
    void escapeXml_lessThan() {
        assertEquals("a&lt;b", JmxWriter.escapeXml("a<b"));
    }

    @Test
    void escapeXml_greaterThan() {
        assertEquals("a&gt;b", JmxWriter.escapeXml("a>b"));
    }

    @Test
    void escapeXml_doubleQuote() {
        assertEquals("a&quot;b", JmxWriter.escapeXml("a\"b"));
    }

    @Test
    void escapeXml_apostrophe() {
        assertEquals("a&apos;b", JmxWriter.escapeXml("a'b"));
    }

    @Test
    void escapeXml_noSpecialChars_returnsSameInstance() {
        String plain = "hello world 123";
        assertSame(plain, JmxWriter.escapeXml(plain),
                "No-op escape must return the original String instance");
    }

    @Test
    void escapeXml_null_returnsEmptyString() {
        assertEquals("", JmxWriter.escapeXml(null));
    }

    @Test
    void specialCharsInTestName_roundTrip() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"6.0\">\n"
                + "  <hashTree>\n"
                + "    <TestPlan testclass=\"TestPlan\" testname=\"Plan &amp; More\">\n"
                + "      <stringProp name=\"q\">SELECT * FROM t WHERE x &lt; 10</stringProp>\n"
                + "    </TestPlan>\n"
                + "    <hashTree/>\n"
                + "  </hashTree>\n"
                + "</jmeterTestPlan>\n";

        PlanNode root     = JmxTreeWalker.parse(xml);
        String   written  = JmxWriter.write(root);
        PlanNode reparsed = JmxTreeWalker.parse(written);

        PlanNode plan = reparsed.getChildren().get(0);
        assertEquals("SELECT * FROM t WHERE x < 10", plan.getStringProp("q"),
                "Escaped entities must survive write→reparse");
    }

    // =========================================================================
    // Non-jmeterTestPlan root
    // =========================================================================

    @Test
    void write_nonRootNode_producesElement() {
        PlanNode node = PlanNode.builder("ThreadGroup", "TG")
                .property("ThreadGroup.num_threads", 5)
                .build();
        String xml = JmxWriter.write(node);
        assertTrue(xml.contains("<ThreadGroup"), "Non-root write must contain the element");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode minimalPlan() {
        PlanNode testPlan = PlanNode.builder("TestPlan", "My Plan").build();
        return PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(testPlan)
                .build();
    }

    /**
     * Wraps a node in a minimal jmeterTestPlan wrapper, writes it, re-parses it,
     * and returns the first child (the original node).
     */
    private static PlanNode roundTrip(PlanNode node) throws Exception {
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(node)
                .build();
        String xml = JmxWriter.write(root);
        PlanNode reparsed = JmxTreeWalker.parse(xml);
        assertFalse(reparsed.getChildren().isEmpty(), "Reparsed root must have children");
        return reparsed.getChildren().get(0);
    }
}
