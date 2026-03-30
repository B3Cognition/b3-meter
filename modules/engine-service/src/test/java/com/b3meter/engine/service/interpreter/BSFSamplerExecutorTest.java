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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BSFSamplerExecutor}.
 */
class BSFSamplerExecutorTest {

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("bsf");
        assertThrows(NullPointerException.class,
                () -> BSFSamplerExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = bsfNode("1+1");
        assertThrows(NullPointerException.class,
                () -> BSFSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = bsfNode("1+1");
        SampleResult r = new SampleResult("bsf");
        assertThrows(NullPointerException.class,
                () -> BSFSamplerExecutor.execute(node, r, null));
    }

    @Test
    void delegatesToJSR223() {
        // BSF should delegate to JSR223 with the configured language
        javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
        if (mgr.getEngineByName("javascript") == null) return; // No JS engine

        PlanNode node = PlanNode.builder("BSFSampler", "bsf-delegate")
                .property("BSFSampler.language", "javascript")
                .property("BSFSampler.script",
                        "SampleResult.setResponseBody('bsf-delegated');")
                .build();

        SampleResult result = new SampleResult("bsf-delegate");
        BSFSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals("bsf-delegated", result.getResponseBody());
    }

    @Test
    void emptyScriptFails() {
        PlanNode node = bsfNode("");
        SampleResult result = new SampleResult("bsf-empty");
        BSFSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    private static PlanNode bsfNode(String script) {
        return PlanNode.builder("BSFSampler", "bsf-test")
                .property("BSFSampler.script", script)
                .property("BSFSampler.language", "javascript")
                .build();
    }
}
