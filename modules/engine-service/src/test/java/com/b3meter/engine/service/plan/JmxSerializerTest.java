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
 * Unit tests for {@link JmxSerializer}.
 *
 * <p>Verifies that {@link JmxSerializer#toJmx(PlanNode)} produces valid
 * JMX XML that can be re-parsed by {@link JmxTreeWalker}, and that all
 * property types appear as the correct XML element tags.
 *
 * <p>Since {@link JmxSerializer} delegates to {@link JmxWriter}, the
 * primary concerns here are the public API contract and the round-trip
 * fidelity with {@link JmxTreeWalker}.
 */
class JmxSerializerTest {

    // =========================================================================
    // Basic output structure
    // =========================================================================

    @Test
    void toJmx_simpleNode_producesXmlDeclaration() {
        PlanNode node = PlanNode.builder("TestPlan", "My Plan").build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.startsWith("<?xml"), "Output must start with XML declaration");
    }

    @Test
    void toJmx_simpleNode_containsJmeterTestPlanElement() {
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .build();
        String xml = JmxSerializer.toJmx(root);
        assertTrue(xml.contains("<jmeterTestPlan"), "Output must contain <jmeterTestPlan");
    }

    @Test
    void toJmx_simpleNode_containsTestPlanName() {
        PlanNode tg = PlanNode.builder("ThreadGroup", "Users").build();
        PlanNode plan = PlanNode.builder("TestPlan", "My Plan").child(tg).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(plan)
                .build();
        String xml = JmxSerializer.toJmx(root);
        assertTrue(xml.contains("My Plan"), "Output must contain the plan name");
    }

    // =========================================================================
    // Round-trip via JmxTreeWalker
    // =========================================================================

    @Test
    void toJmx_thenParse_simpleNode_rootNotNull() throws Exception {
        PlanNode tg = PlanNode.builder("ThreadGroup", "TG 1")
                .property("ThreadGroup.num_threads", 10)
                .build();
        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(plan)
                .build();
        String xml = JmxSerializer.toJmx(root);
        PlanNode reparsed = JmxTreeWalker.parse(xml);
        assertNotNull(reparsed, "JmxTreeWalker.parse must return non-null for valid XML");
    }

    @Test
    void toJmx_thenParse_roundTrip_preservesRootTestClass() throws Exception {
        PlanNode tg = PlanNode.builder("ThreadGroup", "TG 1")
                .property("ThreadGroup.num_threads", 10)
                .property("ThreadGroup.ramp_time", 5)
                .build();
        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(plan)
                .build();
        String xml = JmxSerializer.toJmx(root);
        PlanNode reparsed = JmxTreeWalker.parse(xml);
        assertNotNull(reparsed);
        assertEquals("jmeterTestPlan", reparsed.getTestClass());
    }

    @Test
    void toJmx_thenParse_roundTrip_preservesChildCount() throws Exception {
        PlanNode tg1 = PlanNode.builder("ThreadGroup", "TG1").build();
        PlanNode tg2 = PlanNode.builder("ThreadGroup", "TG2").build();
        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg1).child(tg2).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "jmeterTestPlan")
                .property("version", "1.2")
                .child(plan)
                .build();
        String xml = JmxSerializer.toJmx(root);
        PlanNode reparsed = JmxTreeWalker.parse(xml);
        assertNotNull(reparsed);
        // reparsed root's child is TestPlan; TestPlan's children are the 2 ThreadGroups
        assertFalse(reparsed.getChildren().isEmpty(), "Reparsed tree must have children");
        PlanNode reparsedPlan = reparsed.getChildren().get(0);
        assertEquals(2, reparsedPlan.getChildren().size(),
                "TestPlan must have 2 ThreadGroup children after round-trip");
    }

    // =========================================================================
    // Property type tags
    // =========================================================================

    @Test
    void toJmx_withChildren_producesHashTreeBlocks() {
        PlanNode child = PlanNode.builder("ThreadGroup", "TG").build();
        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(child).build();
        PlanNode root = PlanNode.builder("jmeterTestPlan", "root")
                .property("version", "1.2")
                .child(plan)
                .build();
        String xml = JmxSerializer.toJmx(root);
        assertTrue(xml.contains("<hashTree>") || xml.contains("<hashTree/>"),
                "Output must contain hashTree blocks");
        assertTrue(xml.contains("ThreadGroup"), "Output must contain ThreadGroup element");
    }

    @Test
    void toJmx_stringProperty_producesStringPropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("str", "hello")
                .build();
        // For non-jmeterTestPlan root, JmxWriter writes as a regular node
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<stringProp name=\"str\">hello</stringProp>"),
                "String property must produce <stringProp> tag");
    }

    @Test
    void toJmx_intProperty_producesIntPropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("intVal", 1)
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<intProp name=\"intVal\">1</intProp>"),
                "Integer property must produce <intProp> tag");
    }

    @Test
    void toJmx_longProperty_producesLongPropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("longVal", 1L)
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<longProp name=\"longVal\">1</longProp>"),
                "Long property must produce <longProp> tag");
    }

    @Test
    void toJmx_doubleProperty_producesDoublePropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("dbl", 1.0)
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<doubleProp name=\"dbl\">1.0</doubleProp>"),
                "Double property must produce <doubleProp> tag");
    }

    @Test
    void toJmx_boolProperty_producesBoolPropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("flag", false)
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<boolProp name=\"flag\">false</boolProp>"),
                "Boolean property must produce <boolProp> tag");
    }

    @Test
    void toJmx_allPropertyTypes_producesCorrectTags() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("str", "hello")
                .property("intVal", 1)
                .property("longVal", 1L)
                .property("dbl", 1.0)
                .property("flag", false)
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<stringProp name=\"str\">hello</stringProp>"));
        assertTrue(xml.contains("<intProp name=\"intVal\">1</intProp>"));
        assertTrue(xml.contains("<longProp name=\"longVal\">1</longProp>"));
        assertTrue(xml.contains("<doubleProp name=\"dbl\">1.0</doubleProp>"));
        assertTrue(xml.contains("<boolProp name=\"flag\">false</boolProp>"));
    }

    @Test
    void toJmx_collectionProp_producesCollectionPropTag() {
        PlanNode node = PlanNode.builder("TestPlan", "P")
                .property("myList", List.of("x", "y"))
                .build();
        String xml = JmxSerializer.toJmx(node);
        assertTrue(xml.contains("<collectionProp name=\"myList\">"),
                "List property must produce <collectionProp> tag");
        assertTrue(xml.contains("</collectionProp>"), "collectionProp must be closed");
    }

    // =========================================================================
    // Null guard
    // =========================================================================

    @Test
    void toJmx_nullRoot_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> JmxSerializer.toJmx(null));
    }
}
