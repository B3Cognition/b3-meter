package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RandomVariableConfigExecutor}.
 */
class RandomVariableConfigExecutorTest {

    @AfterEach
    void cleanup() {
        RandomVariableConfigExecutor.resetAll();
    }

    @Test
    void execute_generatesValueInRange() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Random Var")
                .property("variableName", "randVal")
                .property("minimumValue", "1")
                .property("maximumValue", "100")
                .build();

        Map<String, String> variables = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, variables);

        String value = variables.get("randVal");
        assertNotNull(value, "Variable should be set");

        long num = Long.parseLong(value);
        assertTrue(num >= 1 && num <= 100,
                "Value " + num + " should be in range [1, 100]");
    }

    @Test
    void execute_multipleInvocations_generatesVariousValues() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Random")
                .property("variableName", "r")
                .property("minimumValue", "1")
                .property("maximumValue", "1000")
                .build();

        Set<String> values = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Map<String, String> variables = new HashMap<>();
            RandomVariableConfigExecutor.execute(node, variables);
            values.add(variables.get("r"));
        }

        assertTrue(values.size() > 1,
                "Over 50 iterations, should generate different values");
    }

    @Test
    void execute_withOutputFormat() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Formatted")
                .property("variableName", "fmt")
                .property("minimumValue", "1")
                .property("maximumValue", "9")
                .property("outputFormat", "000")
                .build();

        Map<String, String> variables = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, variables);

        String value = variables.get("fmt");
        assertNotNull(value);
        assertEquals(3, value.length(), "Format '000' should produce 3-digit string");
        assertTrue(value.matches("00[1-9]"), "Value should be zero-padded: " + value);
    }

    @Test
    void execute_withSeed_producesReproducibleSequence() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Seeded")
                .property("variableName", "seeded")
                .property("minimumValue", "1")
                .property("maximumValue", "1000000")
                .property("randomSeed", "42")
                .property("perThread", true)
                .build();

        // Run twice with same seed — should produce same first value
        Map<String, String> v1 = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, v1);
        String first = v1.get("seeded");

        RandomVariableConfigExecutor.resetAll();

        Map<String, String> v2 = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, v2);
        String second = v2.get("seeded");

        assertEquals(first, second, "Same seed should produce same first value");
    }

    @Test
    void execute_minGreaterThanMax_swaps() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Swapped")
                .property("variableName", "swap")
                .property("minimumValue", "100")
                .property("maximumValue", "1")
                .build();

        Map<String, String> variables = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, variables);

        long num = Long.parseLong(variables.get("swap"));
        assertTrue(num >= 1 && num <= 100,
                "Should swap min/max and generate in [1, 100]");
    }

    @Test
    void execute_sameMinMax_returnsThatValue() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "Fixed")
                .property("variableName", "fixed")
                .property("minimumValue", "42")
                .property("maximumValue", "42")
                .build();

        Map<String, String> variables = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, variables);

        assertEquals("42", variables.get("fixed"));
    }

    @Test
    void execute_emptyVariableName_skips() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "No Name")
                .property("variableName", "")
                .build();

        Map<String, String> variables = new HashMap<>();
        RandomVariableConfigExecutor.execute(node, variables);

        assertFalse(variables.containsKey(""));
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> RandomVariableConfigExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("RandomVariableConfig", "test")
                .property("variableName", "x")
                .build();
        assertThrows(NullPointerException.class,
                () -> RandomVariableConfigExecutor.execute(node, null));
    }
}
