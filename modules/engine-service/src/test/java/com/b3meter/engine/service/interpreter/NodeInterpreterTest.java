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

import com.b3meter.engine.service.SampleBucket;
import com.b3meter.engine.service.TestRunContext;
import com.b3meter.engine.service.TestRunResult;
import com.b3meter.engine.service.http.HttpClientFactory;
import com.b3meter.engine.service.http.HttpRequest;
import com.b3meter.engine.service.http.HttpResponse;
import com.b3meter.engine.service.plan.JmxTreeWalker;
import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link NodeInterpreter}.
 *
 * <p>All tests use a stub HTTP client — no network required.
 */
class NodeInterpreterTest {

    // =========================================================================
    // Simple plan: TestPlan → ThreadGroup → HTTPSamplerProxy
    // =========================================================================

    @Test
    void simplePlan_executesAndReturnsResult() throws Exception {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Simple">
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Users">
                        <intProp name="ThreadGroup.num_threads">1</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">1</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        StubInterpreterFactory.CapturingBroker broker = StubInterpreterFactory.capturingBroker();
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(), broker);

        TestRunContext ctx = context("run-1", 1);
        TestRunResult result = interpreter.execute(root, ctx);

        // Expect 1 sample (1 VU × 1 loop × 1 HTTP sampler)
        assertEquals(1, result.totalSamples(), "expected 1 sample");
        assertEquals(0, result.errorCount(),   "expected 0 errors");
        assertEquals(TestRunContext.TestRunStatus.STOPPED, result.finalStatus());
        assertEquals("run-1", result.runId());

        // Broker must have published at least one bucket
        assertFalse(broker.publishedBuckets().isEmpty(), "broker should have received buckets");
    }

    @Test
    void simplePlan_twoThreadsOneLoop_producesTwoSamples() throws Exception {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Multi-VU">
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Users">
                        <intProp name="ThreadGroup.num_threads">2</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">1</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        StubInterpreterFactory.CapturingBroker broker = StubInterpreterFactory.capturingBroker();
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(), broker);

        TestRunContext ctx = context("run-2vu", 2);
        TestRunResult result = interpreter.execute(root, ctx);

        assertEquals(2, result.totalSamples(), "2 VUs × 1 loop × 1 sampler = 2");
        assertEquals(0, result.errorCount());
    }

    @Test
    void simplePlan_oneVuThreeLoops_producesThreeSamples() throws Exception {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Loops">
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Users">
                        <intProp name="ThreadGroup.num_threads">1</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">3</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.noOpBroker());

        TestRunContext ctx = context("run-3loops", 1);
        TestRunResult result = interpreter.execute(root, ctx);

        assertEquals(3, result.totalSamples(), "1 VU × 3 loops × 1 sampler = 3");
    }

    // =========================================================================
    // Assertion marks sample as failed
    // =========================================================================

    @Test
    void assertionFailure_incrementsErrorCount() throws Exception {
        // HTTP client returns 200 with body "OK"
        // Assertion requires body equals "EXPECTED" → fails
        HttpClientFactory always200OK = new HttpClientFactory() {
            @Override
            public HttpResponse execute(HttpRequest request) {
                return new HttpResponse(200, "HTTP/1.1", Map.of(),
                        "OK".getBytes(StandardCharsets.UTF_8),
                        0L, 1L, 1L, false);
            }

            @Override
            public void close() {}
        };

        PlanNode root = JmxTreeWalker.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Assert">
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Users">
                        <intProp name="ThreadGroup.num_threads">1</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">1</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree>
                          <ResponseAssertion testclass="ResponseAssertion" testname="Check body">
                            <stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
                            <intProp name="Assertion.test_type">8</intProp>
                            <collectionProp name="Asserion.test_strings">
                              <stringProp name="12345">EXPECTED</stringProp>
                            </collectionProp>
                          </ResponseAssertion>
                          <hashTree/>
                        </hashTree>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """);

        StubInterpreterFactory.CapturingBroker broker = StubInterpreterFactory.capturingBroker();
        NodeInterpreter interpreter = new NodeInterpreter(always200OK, broker);
        TestRunContext ctx = context("run-assert", 1);
        TestRunResult result = interpreter.execute(root, ctx);

        assertEquals(1, result.totalSamples());
        assertEquals(1, result.errorCount(), "assertion failure should count as error");
    }

    // =========================================================================
    // Extractor stores value for downstream use
    // =========================================================================

    @Test
    void extractor_storesVariableForNextSampler() throws Exception {
        // HTTP client always returns {"id":42} for first request, then checks variable on second
        RecordingClient client = new RecordingClient();

        // Build tree programmatically (simpler than inline JMX for this scenario)
        PlanNode httpSampler1 = PlanNode.builder("HTTPSamplerProxy", "Get JSON")
                .property("HTTPSampler.domain",   "api.example.com")
                .property("HTTPSampler.path",     "/data")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        PlanNode extractor = PlanNode.builder("JSONPathExtractor", "extract id")
                .property("JSONPathExtractor.referenceNames", "extractedId")
                .property("JSONPathExtractor.jsonPathExprs",  "$.id")
                .property("JSONPathExtractor.defaultValues",  "NONE")
                .build();

        PlanNode httpSampler2 = PlanNode.builder("HTTPSamplerProxy", "Use var")
                .property("HTTPSampler.domain",   "api.example.com")
                .property("HTTPSampler.path",     "/items/${extractedId}")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        PlanNode samplerWithExtractor = PlanNode.builder("HTTPSamplerProxy", "Get JSON")
                .property("HTTPSampler.domain",   "api.example.com")
                .property("HTTPSampler.path",     "/data")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .child(extractor)
                .build();

        PlanNode threadGroup = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller",
                        PlanNode.builder("LoopController", "ctrl")
                                .property("LoopController.loops", 1)
                                .build())
                .child(samplerWithExtractor)
                .child(httpSampler2)
                .build();

        PlanNode testPlan = PlanNode.builder("TestPlan", "Plan")
                .child(threadGroup)
                .build();

        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        TestRunContext ctx = context("run-extract", 1);
        TestRunResult result = interpreter.execute(testPlan, ctx);

        // 2 samples (sampler1 + sampler2)
        assertEquals(2, result.totalSamples());

        // Verify that the second request used the extracted variable
        assertTrue(client.requestedUrls.stream().anyMatch(u -> u.contains("/items/42")),
                "Second sampler should use extracted variable. URLs: " + client.requestedUrls);
    }

    // =========================================================================
    // Result fields
    // =========================================================================

    @Test
    void result_hasCorrectRunId() throws Exception {
        PlanNode plan = minimalPlan();
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.noOpBroker());

        TestRunResult result = interpreter.execute(plan, context("my-run-id", 1));

        assertEquals("my-run-id", result.runId());
    }

    @Test
    void result_elapsedIsNonNegative() throws Exception {
        PlanNode plan = minimalPlan();
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.noOpBroker());

        TestRunResult result = interpreter.execute(plan, context("elapsed-test", 1));

        assertFalse(result.elapsed().isNegative(), "elapsed time must not be negative");
    }

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void nullRoot_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        assertThrows(NullPointerException.class,
                () -> interpreter.execute(null, context("x", 1)));
    }

    @Test
    void nullContext_throws() throws Exception {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        PlanNode plan = minimalPlan();
        assertThrows(NullPointerException.class,
                () -> interpreter.execute(plan, null));
    }

    // =========================================================================
    // JMX scheduler + duration + ramp-up tests
    // =========================================================================

    @Test
    void schedulerDuration_honorsJmxDuration_runsForConfiguredTime() throws Exception {
        // JMX: loops=-1 (infinite), scheduler=true, duration=3s
        // Expected: runs ~3 seconds, produces many samples (not just 5)
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Duration Test"/>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Timed Group">
                        <intProp name="ThreadGroup.num_threads">2</intProp>
                        <boolProp name="ThreadGroup.scheduler">true</boolProp>
                        <stringProp name="ThreadGroup.duration">3</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">0</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">-1</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.capturingBroker());

        // context has durationSeconds=0 (not set via API) — JMX scheduler should take over
        TestRunContext ctx = context("sched-1", 2);

        long startMs = System.currentTimeMillis();
        TestRunResult result = interpreter.execute(root, ctx);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // Should have run for approximately 3 seconds (tolerance: 2-6s for CI variance)
        assertTrue(elapsedMs >= 2000, "expected at least 2s elapsed, got " + elapsedMs + "ms");
        assertTrue(elapsedMs < 8000, "expected less than 8s elapsed, got " + elapsedMs + "ms");

        // With 2 VUs running infinite loops for 3s against a no-op client, expect many samples
        assertTrue(result.totalSamples() > 10,
                "expected >10 samples from 3s timed run, got " + result.totalSamples());
    }

    @Test
    void schedulerDuration_apiOverridesJmx() throws Exception {
        // JMX says duration=60s, but API context says durationSeconds=2
        // Expected: stops at ~2s (API wins)
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Override Test"/>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Long Group">
                        <intProp name="ThreadGroup.num_threads">1</intProp>
                        <boolProp name="ThreadGroup.scheduler">true</boolProp>
                        <stringProp name="ThreadGroup.duration">60</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">-1</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.capturingBroker());

        // API sets durationSeconds=2 — should override JMX's 60s
        TestRunContext ctx = TestRunContext.builder()
                .runId("override-1")
                .planPath("test.jmx")
                .virtualUsers(1)
                .durationSeconds(2)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        long startMs = System.currentTimeMillis();
        interpreter.execute(root, ctx);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // Must stop well before the JMX 60s — should be ~2s
        assertTrue(elapsedMs < 8000,
                "API override should stop at ~2s, but ran for " + elapsedMs + "ms");
    }

    @Test
    void noScheduler_loopCountRespected() throws Exception {
        // JMX: scheduler=false (default), loops=3
        // Expected: exactly 3 samples per VU, finishes quickly
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testclass="TestPlan" testname="Loop Test"/>
                    <hashTree>
                      <ThreadGroup testclass="ThreadGroup" testname="Loop Group">
                        <intProp name="ThreadGroup.num_threads">1</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <intProp name="LoopController.loops">3</intProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">
                          <stringProp name="HTTPSampler.domain">example.com</stringProp>
                          <stringProp name="HTTPSampler.path">/</stringProp>
                          <stringProp name="HTTPSampler.method">GET</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        PlanNode root = JmxTreeWalker.parse(jmx);
        NodeInterpreter interpreter = new NodeInterpreter(
                StubInterpreterFactory.noOpHttpClient(),
                StubInterpreterFactory.capturingBroker());

        TestRunContext ctx = context("loop-1", 1);
        TestRunResult result = interpreter.execute(root, ctx);

        // 1 VU × 3 loops × 1 sampler = 3 samples
        assertEquals(3, result.totalSamples(),
                "expected exactly 3 samples from loop-count plan");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static TestRunContext context(String runId, int vus) {
        return TestRunContext.builder()
                .runId(runId)
                .planPath("test.jmx")
                .virtualUsers(vus)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();
    }

    private static PlanNode minimalPlan() {
        PlanNode http = PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "example.com")
                .property("HTTPSampler.path",     "/")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 1)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(http)
                .build();

        return PlanNode.builder("TestPlan", "Plan").child(tg).build();
    }

    // =========================================================================
    // Recording HTTP client
    // =========================================================================

    private static final class RecordingClient implements HttpClientFactory {

        final List<String> requestedUrls = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public HttpResponse execute(HttpRequest request) {
            requestedUrls.add(request.url());
            String body = "{\"id\": 42}";
            return new HttpResponse(200, "HTTP/1.1", Map.of(),
                    body.getBytes(StandardCharsets.UTF_8),
                    0L, 1L, 1L, false);
        }

        @Override
        public void close() {}
    }
}
