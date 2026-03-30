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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BoundaryExtractorExecutor}.
 */
class BoundaryExtractorExecutorTest {

    @Test
    void extractsBetweenBoundaries() {
        PlanNode node = boundaryNode("TOKEN", "token\":\"", "\"", "NOT_FOUND", "1");
        SampleResult result = sampleWithBody("{\"token\":\"abc123\",\"user\":\"bob\"}");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("abc123", vars.get("TOKEN"));
    }

    @Test
    void secondMatch() {
        PlanNode node = boundaryNode("VAL", "[", "]", "NONE", "2");
        SampleResult result = sampleWithBody("[first][second][third]");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("second", vars.get("VAL"));
    }

    @Test
    void allMatches() {
        PlanNode node = boundaryNode("VAL", "[", "]", "NONE", "-1");
        SampleResult result = sampleWithBody("[alpha][beta][gamma]");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("alpha", vars.get("VAL"));
        assertEquals("alpha", vars.get("VAL_1"));
        assertEquals("beta", vars.get("VAL_2"));
        assertEquals("gamma", vars.get("VAL_3"));
        assertEquals("3", vars.get("VAL_matchNr"));
    }

    @Test
    void noMatch_returnsDefault() {
        PlanNode node = boundaryNode("X", "START", "END", "DEFAULT", "1");
        SampleResult result = sampleWithBody("no boundaries here");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("DEFAULT", vars.get("X"));
    }

    @Test
    void emptyBody_returnsDefault() {
        PlanNode node = boundaryNode("X", "[", "]", "FALLBACK", "1");
        SampleResult result = sampleWithBody("");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("FALLBACK", vars.get("X"));
    }

    @Test
    void emptyBoundaries_extractsAll() {
        PlanNode node = boundaryNode("ALL", "", "", "NONE", "1");
        SampleResult result = sampleWithBody("everything");
        Map<String, String> vars = new HashMap<>();
        BoundaryExtractorExecutor.execute(node, result, vars);
        assertEquals("everything", vars.get("ALL"));
    }

    @Test
    void findBetweenBoundaries_unit() {
        List<String> results = BoundaryExtractorExecutor.findBetweenBoundaries(
                "a=1&b=2&c=3", "=", "&");
        assertEquals(2, results.size());
        assertEquals("1", results.get(0));
        assertEquals("2", results.get(1));
    }

    @Test
    void nullNode_throws() {
        assertThrows(NullPointerException.class, () ->
                BoundaryExtractorExecutor.execute(null, new SampleResult("x"), new HashMap<>()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode boundaryNode(String refName, String lBoundary, String rBoundary,
                                          String defaultVal, String matchNumber) {
        return PlanNode.builder("BoundaryExtractor", "boundary-" + refName)
                .property("BoundaryExtractor.refname", refName)
                .property("BoundaryExtractor.lboundary", lBoundary)
                .property("BoundaryExtractor.rboundary", rBoundary)
                .property("BoundaryExtractor.default", defaultVal)
                .property("BoundaryExtractor.match_number", matchNumber)
                .build();
    }

    private static SampleResult sampleWithBody(String body) {
        SampleResult result = new SampleResult("sampler");
        result.setStatusCode(200);
        result.setResponseBody(body);
        return result;
    }
}
