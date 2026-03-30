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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConstantThroughputTimerExecutor}.
 */
class ConstantThroughputTimerExecutorTest {

    @AfterEach
    void tearDown() {
        ConstantThroughputTimerExecutor.resetThreadLocal();
    }

    @Test
    void computeDelay_60perMinute_returns1000msInterval() {
        // 60 samples/min = 1 sample/sec = 1000ms interval
        // If 200ms elapsed, delay should be 800ms
        long delay = ConstantThroughputTimerExecutor.computeDelay(60.0, 200);
        assertEquals(800, delay);
    }

    @Test
    void computeDelay_zeroThroughput_returnsZero() {
        long delay = ConstantThroughputTimerExecutor.computeDelay(0.0, 100);
        assertEquals(0, delay);
    }

    @Test
    void computeDelay_negativeThroughput_returnsZero() {
        long delay = ConstantThroughputTimerExecutor.computeDelay(-10.0, 100);
        assertEquals(0, delay);
    }

    @Test
    void computeDelay_elapsedExceedsInterval_returnsZero() {
        // 60/min = 1000ms interval; 1500ms elapsed => no delay needed
        long delay = ConstantThroughputTimerExecutor.computeDelay(60.0, 1500);
        assertEquals(0, delay);
    }

    @Test
    void computeDelay_120perMinute_returns500msInterval() {
        // 120 samples/min = 500ms interval; 100ms elapsed => 400ms delay
        long delay = ConstantThroughputTimerExecutor.computeDelay(120.0, 100);
        assertEquals(400, delay);
    }

    @Test
    void execute_withValidThroughput_doesNotThrow() {
        PlanNode node = PlanNode.builder("ConstantThroughputTimer", "CT Timer")
                .property("throughput", "6000") // very high throughput = near-zero delay
                .build();

        assertDoesNotThrow(() -> ConstantThroughputTimerExecutor.execute(node));
    }

    @Test
    void execute_withZeroThroughput_noDelay() {
        PlanNode node = PlanNode.builder("ConstantThroughputTimer", "CT Timer")
                .property("throughput", "0")
                .build();

        long start = System.currentTimeMillis();
        ConstantThroughputTimerExecutor.execute(node);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 50, "zero throughput should cause no delay, elapsed: " + elapsed);
    }

    @Test
    void execute_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> ConstantThroughputTimerExecutor.execute(null));
    }
}
