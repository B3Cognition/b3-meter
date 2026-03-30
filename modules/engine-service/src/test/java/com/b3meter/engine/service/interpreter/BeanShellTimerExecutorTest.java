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
