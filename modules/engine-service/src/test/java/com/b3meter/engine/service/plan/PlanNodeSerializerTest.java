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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlanNodeSerializer}.
 *
 * <p>Verifies that {@link PlanNode} trees survive a serialize → deserialize
 * round-trip with all property types preserved, including the critical
 * Integer vs. Long distinction (see FR-009.002 and SC-009.005).
 */
class PlanNodeSerializerTest {

    // =========================================================================
    // Round-trip: primitive property types
    // =========================================================================

    @Test
    void serialize_thenDeserialize_stringProp_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "My Plan")
                .property("str", "hello world")
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals("hello world", restored.getStringProp("str"));
    }

    @Test
    void serialize_thenDeserialize_intProp_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("intVal", 42)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals(42, restored.getIntProp("intVal"));
    }

    @Test
    void serialize_thenDeserialize_longProp_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("longVal", 9_000_000_000L)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals(9_000_000_000L, restored.getLongProp("longVal"),
                "Long value must not be truncated to Integer range");
    }

    @Test
    void serialize_thenDeserialize_doubleProp_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("dbl", 3.14)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals(3.14, restored.getDoubleProp("dbl"), 1e-10);
    }

    @Test
    void serialize_thenDeserialize_boolPropTrue_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("flag", true)
                .build();
        PlanNode restored = roundTrip(original);
        assertTrue(restored.getBoolProp("flag"));
    }

    @Test
    void serialize_thenDeserialize_boolPropFalse_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("flag", false)
                .build();
        PlanNode restored = roundTrip(original);
        assertFalse(restored.getBoolProp("flag"));
    }

    @Test
    void serialize_thenDeserialize_allPrimitiveTypes_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "My Plan")
                .property("str", "hello")
                .property("intVal", 42)
                .property("longVal", 9_000_000_000L)
                .property("dbl", 1.5)
                .property("flag", true)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals("hello", restored.getStringProp("str"));
        assertEquals(42, restored.getIntProp("intVal"));
        assertEquals(9_000_000_000L, restored.getLongProp("longVal"));
        assertEquals(1.5, restored.getDoubleProp("dbl"), 1e-10);
        assertTrue(restored.getBoolProp("flag"));
    }

    // =========================================================================
    // Round-trip: nested PlanNode (elementProp)
    // =========================================================================

    @Test
    void serialize_thenDeserialize_nestedPlanNode_preserved() throws Exception {
        PlanNode inner = PlanNode.builder("Arguments", "User Vars").build();
        PlanNode original = PlanNode.builder("TestPlan", "Plan")
                .property("args", inner)
                .build();
        PlanNode restored = roundTrip(original);
        PlanNode restoredInner = restored.getElementProp("args");
        assertNotNull(restoredInner, "Nested PlanNode property must be non-null after round-trip");
        assertEquals("Arguments", restoredInner.getTestClass());
        assertEquals("User Vars", restoredInner.getTestName());
    }

    @Test
    void serialize_thenDeserialize_nestedPlanNodeWithProps_preserved() throws Exception {
        PlanNode inner = PlanNode.builder("Arguments", "Vars")
                .property("count", 5)
                .build();
        PlanNode original = PlanNode.builder("TestPlan", "Plan")
                .property("elementPropKey", inner)
                .build();
        PlanNode restored = roundTrip(original);
        PlanNode restoredInner = restored.getElementProp("elementPropKey");
        assertNotNull(restoredInner);
        assertEquals(5, restoredInner.getIntProp("count"));
    }

    // =========================================================================
    // Round-trip: collection prop
    // =========================================================================

    @Test
    void serialize_thenDeserialize_collectionProp_stringItems_preserved() throws Exception {
        PlanNode original = PlanNode.builder("ThreadGroup", "TG")
                .property("items", List.of("a", "b", "c"))
                .build();
        PlanNode restored = roundTrip(original);
        List<Object> items = restored.getCollectionProp("items");
        assertEquals(3, items.size());
        assertEquals("a", items.get(0));
        assertEquals("b", items.get(1));
        assertEquals("c", items.get(2));
    }

    @Test
    void serialize_thenDeserialize_collectionProp_mixedItems_preserved() throws Exception {
        PlanNode original = PlanNode.builder("ThreadGroup", "TG")
                .property("items", List.of("a", "b", 3))
                .build();
        PlanNode restored = roundTrip(original);
        List<Object> items = restored.getCollectionProp("items");
        assertEquals(3, items.size());
        assertEquals("a", items.get(0));
        assertEquals("b", items.get(1));
        assertEquals(3, items.get(2));
    }

    // =========================================================================
    // Round-trip: child hierarchy
    // =========================================================================

    @Test
    void serialize_thenDeserialize_childHierarchy_preserved() throws Exception {
        PlanNode child = PlanNode.builder("HTTPSamplerProxy", "Login").build();
        PlanNode original = PlanNode.builder("ThreadGroup", "TG").child(child).build();
        PlanNode restored = roundTrip(original);
        assertEquals(1, restored.getChildren().size());
        assertEquals("HTTPSamplerProxy", restored.getChildren().get(0).getTestClass());
        assertEquals("Login", restored.getChildren().get(0).getTestName());
    }

    @Test
    void serialize_thenDeserialize_multiLevel_hierarchy_preserved() throws Exception {
        PlanNode sampler = PlanNode.builder("HTTPSamplerProxy", "GET").build();
        PlanNode tg = PlanNode.builder("ThreadGroup", "TG").child(sampler).build();
        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        PlanNode restored = roundTrip(plan);
        assertEquals(1, restored.getChildren().size());
        PlanNode restoredTg = restored.getChildren().get(0);
        assertEquals("ThreadGroup", restoredTg.getTestClass());
        assertEquals(1, restoredTg.getChildren().size());
        assertEquals("HTTPSamplerProxy", restoredTg.getChildren().get(0).getTestClass());
    }

    @Test
    void serialize_thenDeserialize_emptyChildren_preserved() throws Exception {
        PlanNode original = PlanNode.builder("TestPlan", "Plan").build();
        PlanNode restored = roundTrip(original);
        assertEquals(0, restored.getChildren().size());
    }

    // =========================================================================
    // Output format
    // =========================================================================

    @Test
    void serialize_producesJsonStartingWithBrace() throws Exception {
        PlanNode node = PlanNode.builder("TestPlan", "P").build();
        String json = PlanNodeSerializer.serialize(node);
        assertTrue(json.trim().startsWith("{"),
                "Serialized output must start with '{' (JSON object)");
    }

    @Test
    void serialize_testClassAndTestName_presentInJson() throws Exception {
        PlanNode node = PlanNode.builder("ThreadGroup", "My TG").build();
        String json = PlanNodeSerializer.serialize(node);
        assertTrue(json.contains("ThreadGroup"), "JSON must contain testClass");
        assertTrue(json.contains("My TG"), "JSON must contain testName");
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Test
    void deserialize_malformedJson_throwsJsonProcessingException() {
        assertThrows(JsonProcessingException.class,
                () -> PlanNodeSerializer.deserialize("not-json"),
                "Malformed JSON must throw JsonProcessingException");
    }

    @Test
    void deserialize_emptyJson_throwsJsonProcessingException() {
        assertThrows(JsonProcessingException.class,
                () -> PlanNodeSerializer.deserialize(""),
                "Empty input must throw JsonProcessingException");
    }

    // =========================================================================
    // Integer vs Long type fidelity (SC-009.005 / FR-009.002)
    // =========================================================================

    @Test
    void intProp_doesNotBecomeZero_afterRoundTrip() throws Exception {
        // getIntProp returns 0 if the value is not instanceof Integer.
        // If int gets stored/restored as Long, getIntProp would return 0 — this must not happen.
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("threads", 50)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals(50, restored.getIntProp("threads"),
                "Integer property must round-trip as Integer, not as Long");
    }

    @Test
    void longProp_doesNotBecomeZero_afterRoundTrip() throws Exception {
        // getLongProp returns 0L if the value is not instanceof Long (or Integer).
        // We specifically want getLongProp to return the long value after round-trip.
        PlanNode original = PlanNode.builder("TestPlan", "P")
                .property("bytes", 5_000_000_000L)
                .build();
        PlanNode restored = roundTrip(original);
        assertEquals(5_000_000_000L, restored.getLongProp("bytes"),
                "Long property must round-trip as Long");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode roundTrip(PlanNode node) throws JsonProcessingException {
        String json = PlanNodeSerializer.serialize(node);
        return PlanNodeSerializer.deserialize(json);
    }
}
