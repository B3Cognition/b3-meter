package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BeanShellTimerExecutor}.
 */
class BeanShellTimerExecutorTest {

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> BeanShellTimerExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("BeanShellTimer", "timer").build();
        assertThrows(NullPointerException.class,
                () -> BeanShellTimerExecutor.execute(node, null));
    }

    @Test
    void execute_emptyScript_noException() {
        PlanNode node = PlanNode.builder("BeanShellTimer", "timer")
                .property("BeanShellTimer.script", "")
                .build();

        // Should not throw — empty script means no delay
        BeanShellTimerExecutor.execute(node, new HashMap<>());
    }

    @Test
    void execute_delegatesToJSR223() {
        // Script that returns 0 delay — should complete immediately
        PlanNode node = PlanNode.builder("BeanShellTimer", "timer")
                .property("BeanShellTimer.script", "0")
                .build();

        long start = System.currentTimeMillis();
        BeanShellTimerExecutor.execute(node, new HashMap<>());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 500, "Timer returning 0 should complete quickly");
    }
}
