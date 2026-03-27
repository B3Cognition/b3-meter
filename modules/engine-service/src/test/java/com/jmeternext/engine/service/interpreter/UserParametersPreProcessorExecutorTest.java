package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserParametersPreProcessorExecutor}.
 */
class UserParametersPreProcessorExecutorTest {

    @BeforeEach
    void resetCounters() {
        UserParametersPreProcessorExecutor.resetCounters();
    }

    @Test
    void execute_setsVariablesFromFirstValueSet() {
        List<Object> names = List.of("username", "password");
        List<Object> valueSet1 = List.of("alice", "pass1");
        List<Object> valueSet2 = List.of("bob", "pass2");
        List<Object> threadValues = List.of(valueSet1, valueSet2);

        PlanNode node = PlanNode.builder("UserParameters", "User Params")
                .property("UserParameters.names", names)
                .property("UserParameters.thread_values", threadValues)
                .build();

        Map<String, String> variables = new HashMap<>();
        UserParametersPreProcessorExecutor.execute(node, variables);

        assertEquals("alice", variables.get("username"));
        assertEquals("pass1", variables.get("password"));
    }

    @Test
    void execute_roundRobinsThroughValueSets() {
        List<Object> names = List.of("user");
        List<Object> vs1 = List.of("user1");
        List<Object> vs2 = List.of("user2");
        List<Object> threadValues = List.of(vs1, vs2);

        PlanNode node = PlanNode.builder("UserParameters", "RR Params")
                .property("UserParameters.names", names)
                .property("UserParameters.thread_values", threadValues)
                .build();

        Map<String, String> variables = new HashMap<>();

        // First call: vs1
        UserParametersPreProcessorExecutor.execute(node, variables);
        assertEquals("user1", variables.get("user"));

        // Second call: vs2
        UserParametersPreProcessorExecutor.execute(node, variables);
        assertEquals("user2", variables.get("user"));

        // Third call: wraps around to vs1
        UserParametersPreProcessorExecutor.execute(node, variables);
        assertEquals("user1", variables.get("user"));
    }

    @Test
    void execute_emptyNames_skips() {
        PlanNode node = PlanNode.builder("UserParameters", "Empty")
                .property("UserParameters.names", List.of())
                .property("UserParameters.thread_values", List.of())
                .build();

        Map<String, String> variables = new HashMap<>();
        // Should not throw
        UserParametersPreProcessorExecutor.execute(node, variables);
        assertTrue(variables.isEmpty());
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> UserParametersPreProcessorExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("UserParameters", "test")
                .property("UserParameters.names", List.of("a"))
                .property("UserParameters.thread_values", List.of(List.of("1")))
                .build();
        assertThrows(NullPointerException.class,
                () -> UserParametersPreProcessorExecutor.execute(node, null));
    }

    @Test
    void execute_fewerValuesThanNames_usesEmptyForMissing() {
        List<Object> names = List.of("a", "b", "c");
        List<Object> valueSet = List.of("v1");  // Only one value for three names
        List<Object> threadValues = List.of(valueSet);

        PlanNode node = PlanNode.builder("UserParameters", "Short values")
                .property("UserParameters.names", names)
                .property("UserParameters.thread_values", threadValues)
                .build();

        Map<String, String> variables = new HashMap<>();
        UserParametersPreProcessorExecutor.execute(node, variables);

        assertEquals("v1", variables.get("a"));
        assertEquals("", variables.get("b"), "Missing values should default to empty string");
        assertEquals("", variables.get("c"));
    }
}
