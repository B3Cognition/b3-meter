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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JSR223TimerExecutor}.
 */
class JSR223TimerExecutorTest {

    @Test
    void parseDelay_fromLong() {
        assertEquals(500L, JSR223TimerExecutor.parseDelay(500L));
    }

    @Test
    void parseDelay_fromInteger() {
        assertEquals(300L, JSR223TimerExecutor.parseDelay(300));
    }

    @Test
    void parseDelay_fromDouble() {
        assertEquals(250L, JSR223TimerExecutor.parseDelay(250.7));
    }

    @Test
    void parseDelay_fromString() {
        assertEquals(100L, JSR223TimerExecutor.parseDelay("100"));
    }

    @Test
    void parseDelay_negativeReturnsClamped() {
        assertEquals(0L, JSR223TimerExecutor.parseDelay(-50));
    }

    @Test
    void parseDelay_nullReturnsZero() {
        assertEquals(0L, JSR223TimerExecutor.parseDelay(null));
    }

    @Test
    void parseDelay_nonNumericStringReturnsZero() {
        assertEquals(0L, JSR223TimerExecutor.parseDelay("abc"));
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> JSR223TimerExecutor.execute(null, new HashMap<>()));
    }

    @Test
    void execute_nullVariables_throws() {
        PlanNode node = PlanNode.builder("JSR223Timer", "timer").build();
        assertThrows(NullPointerException.class,
                () -> JSR223TimerExecutor.execute(node, null));
    }

    @Test
    void execute_emptyScript_noException() {
        PlanNode node = PlanNode.builder("JSR223Timer", "timer")
                .property("JSR223Timer.script", "")
                .build();

        // Should not throw
        JSR223TimerExecutor.execute(node, new HashMap<>());
    }

    @Test
    void execute_scriptReturningZero_completesImmediately() {
        PlanNode node = PlanNode.builder("JSR223Timer", "timer")
                .property("JSR223Timer.script", "0")
                .property("JSR223Timer.scriptLanguage", "javascript")
                .build();

        long start = System.currentTimeMillis();
        JSR223TimerExecutor.execute(node, new HashMap<>());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 500, "Timer returning 0 should complete quickly, took " + elapsed + " ms");
    }
}
