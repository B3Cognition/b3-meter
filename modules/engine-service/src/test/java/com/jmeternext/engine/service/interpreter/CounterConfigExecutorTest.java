package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CounterConfigExecutor}.
 */
class CounterConfigExecutorTest {

    @AfterEach
    void cleanup() {
        CounterConfigExecutor.resetCounters();
    }

    @Test
    void execute_perUser_incrementsSequentially() {
        PlanNode node = PlanNode.builder("CounterConfig", "Counter")
                .property("CounterConfig.start", "1")
                .property("CounterConfig.end", "")
                .property("CounterConfig.incr", "1")
                .property("CounterConfig.name", "counter")
                .property("CounterConfig.per_user", true)
                .build();

        Map<String, String> variables = new HashMap<>();

        CounterConfigExecutor.execute(node, variables);
        assertEquals("1", variables.get("counter"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("2", variables.get("counter"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("3", variables.get("counter"));
    }

    @Test
    void execute_perUser_wrapsAtEnd() {
        PlanNode node = PlanNode.builder("CounterConfig", "Counter")
                .property("CounterConfig.start", "1")
                .property("CounterConfig.end", "3")
                .property("CounterConfig.incr", "1")
                .property("CounterConfig.name", "cnt")
                .property("CounterConfig.per_user", true)
                .build();

        Map<String, String> variables = new HashMap<>();

        CounterConfigExecutor.execute(node, variables);
        assertEquals("1", variables.get("cnt"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("2", variables.get("cnt"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("3", variables.get("cnt"));

        // Should wrap back to start
        CounterConfigExecutor.execute(node, variables);
        assertEquals("1", variables.get("cnt"));
    }

    @Test
    void execute_sharedCounter_threadSafe() {
        PlanNode node = PlanNode.builder("CounterConfig", "Shared Counter")
                .property("CounterConfig.start", "100")
                .property("CounterConfig.end", "")
                .property("CounterConfig.incr", "10")
                .property("CounterConfig.name", "shared")
                .property("CounterConfig.per_user", false)
                .build();

        Map<String, String> v1 = new HashMap<>();
        Map<String, String> v2 = new HashMap<>();

        CounterConfigExecutor.execute(node, v1);
        assertEquals("100", v1.get("shared"));

        CounterConfigExecutor.execute(node, v2);
        assertEquals("110", v2.get("shared"));

        CounterConfigExecutor.execute(node, v1);
        assertEquals("120", v1.get("shared"));
    }

    @Test
    void execute_withFormat() {
        PlanNode node = PlanNode.builder("CounterConfig", "Formatted Counter")
                .property("CounterConfig.start", "1")
                .property("CounterConfig.incr", "1")
                .property("CounterConfig.name", "fmtCounter")
                .property("CounterConfig.format", "000")
                .property("CounterConfig.per_user", true)
                .build();

        Map<String, String> variables = new HashMap<>();

        CounterConfigExecutor.execute(node, variables);
        assertEquals("001", variables.get("fmtCounter"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("002", variables.get("fmtCounter"));
    }

    @Test
    void execute_customIncrement() {
        PlanNode node = PlanNode.builder("CounterConfig", "Counter")
                .property("CounterConfig.start", "0")
                .property("CounterConfig.incr", "5")
                .property("CounterConfig.name", "step5")
                .property("CounterConfig.per_user", true)
                .build();

        Map<String, String> variables = new HashMap<>();

        CounterConfigExecutor.execute(node, variables);
        assertEquals("0", variables.get("step5"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("5", variables.get("step5"));

        CounterConfigExecutor.execute(node, variables);
        assertEquals("10", variables.get("step5"));
    }

    @Test
    void execute_emptyVariableName_skips() {
        PlanNode node = PlanNode.builder("CounterConfig", "No Name")
                .property("CounterConfig.name", "")
                .build();

        Map<String, String> variables = new HashMap<>();
        CounterConfigExecutor.execute(node, variables);

        // No variable should be set (only internal tracking)
        assertFalse(variables.containsKey(""));
    }

    @Test
    void execute_defaultValues() {
        PlanNode node = PlanNode.builder("CounterConfig", "Default Counter")
                .property("CounterConfig.name", "defCnt")
                .build();

        Map<String, String> variables = new HashMap<>();
        CounterConfigExecutor.execute(node, variables);

        // Default start=1, incr=1
        assertEquals("1", variables.get("defCnt"));
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> CounterConfigExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("CounterConfig", "test")
                .property("CounterConfig.name", "x")
                .build();
        assertThrows(NullPointerException.class,
                () -> CounterConfigExecutor.execute(node, null));
    }
}
