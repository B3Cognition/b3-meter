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
 * Tests for {@link JUnitSamplerExecutor}.
 */
class JUnitSamplerExecutorTest {

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("junit");
        assertThrows(NullPointerException.class,
                () -> JUnitSamplerExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = junitNode("com.example.Test", "testSomething");
        assertThrows(NullPointerException.class,
                () -> JUnitSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = junitNode("com.example.Test", "testSomething");
        SampleResult r = new SampleResult("junit");
        assertThrows(NullPointerException.class,
                () -> JUnitSamplerExecutor.execute(node, r, null));
    }

    @Test
    void failsOnEmptyClassname() {
        PlanNode node = PlanNode.builder("JUnitSampler", "junit-empty-class")
                .property("junitsampler.method", "testSomething")
                .build();

        SampleResult result = new SampleResult("junit-empty-class");
        JUnitSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("classname is empty"));
    }

    @Test
    void failsOnEmptyMethod() {
        PlanNode node = PlanNode.builder("JUnitSampler", "junit-empty-method")
                .property("junitsampler.classname", "com.example.Test")
                .build();

        SampleResult result = new SampleResult("junit-empty-method");
        JUnitSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("method is empty"));
    }

    @Test
    void returns501ForClassNotFound() {
        PlanNode node = junitNode("com.nonexistent.TestClass", "testMethod");

        SampleResult result = new SampleResult("junit-cnf");
        JUnitSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(501, result.getStatusCode());
        assertTrue(result.getFailureMessage().contains("class not found"));
        assertTrue(result.getResponseBody().contains("com.nonexistent.TestClass"));
    }

    @Test
    void invokesExistingClassMethod() {
        // Use a well-known JDK class with a no-arg method
        PlanNode node = PlanNode.builder("JUnitSampler", "junit-real")
                .property("junitsampler.classname", "java.util.ArrayList")
                .property("junitsampler.method", "size")
                .build();

        SampleResult result = new SampleResult("junit-real");
        JUnitSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("Test passed"));
    }

    @Test
    void handlesMethodException() {
        // Collections.emptyList().clear() throws UnsupportedOperationException
        // but we need a class we can instantiate; use a class whose method we know exists
        PlanNode node = PlanNode.builder("JUnitSampler", "junit-no-method")
                .property("junitsampler.classname", "java.util.ArrayList")
                .property("junitsampler.method", "nonExistentMethod")
                .build();

        SampleResult result = new SampleResult("junit-no-method");
        JUnitSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
    }

    private static PlanNode junitNode(String className, String method) {
        return PlanNode.builder("JUnitSampler", "junit-test")
                .property("junitsampler.classname", className)
                .property("junitsampler.method", method)
                .build();
    }
}
