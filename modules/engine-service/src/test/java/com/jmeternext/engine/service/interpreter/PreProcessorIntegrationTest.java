package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.TestRunResult;
import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.http.HttpRequest;
import com.jmeternext.engine.service.http.HttpResponse;
import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying the pre-processor execution phase in {@link NodeInterpreter}.
 *
 * <p>Tests that pre-processors run before samplers and modify variables that
 * the sampler then uses.
 */
class PreProcessorIntegrationTest {

    /** Returns true if a JavaScript engine is available on this JDK. */
    private static boolean hasJavaScriptEngine() {
        return new javax.script.ScriptEngineManager().getEngineByName("javascript") != null;
    }

    @Test
    void preProcessor_setsVariable_usedBySampler() throws Exception {
        if (!hasJavaScriptEngine()) return;

        // Build a plan where a JSR223PreProcessor sets a variable,
        // and the HTTP sampler uses it in its path.
        RecordingClient client = new RecordingClient();

        PlanNode preProcessor = PlanNode.builder("JSR223PreProcessor", "Set path var")
                .property("scriptLanguage", "javascript")
                .property("script", "vars.put('dynamicPath', '/api/data');")
                .build();

        PlanNode httpSampler = PlanNode.builder("HTTPSamplerProxy", "Dynamic GET")
                .property("HTTPSampler.domain", "example.com")
                .property("HTTPSampler.path", "${dynamicPath}")
                .property("HTTPSampler.method", "GET")
                .property("HTTPSampler.protocol", "http")
                .child(preProcessor)  // Pre-processor is a child of the sampler
                .build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 1)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(httpSampler)
                .build();

        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        TestRunContext ctx = TestRunContext.builder()
                .runId("pre-proc-test")
                .planPath("test.jmx")
                .virtualUsers(1)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        TestRunResult result = interpreter.execute(plan, ctx);

        assertEquals(1, result.totalSamples());
        assertTrue(client.requestedUrls.stream().anyMatch(u -> u.contains("/api/data")),
                "HTTP sampler should use the variable set by the pre-processor. URLs: "
                        + client.requestedUrls);
    }

    @Test
    void userParametersPreProcessor_setsVariable_usedBySampler() throws Exception {
        // UserParameters pre-processor sets a variable without needing a script engine
        RecordingClient client = new RecordingClient();

        PlanNode userParams = PlanNode.builder("UserParameters", "Set user")
                .property("UserParameters.names", List.of("apiPath"))
                .property("UserParameters.thread_values", List.of(List.of("/api/users")))
                .build();

        PlanNode httpSampler = PlanNode.builder("HTTPSamplerProxy", "Dynamic GET")
                .property("HTTPSampler.domain", "example.com")
                .property("HTTPSampler.path", "${apiPath}")
                .property("HTTPSampler.method", "GET")
                .property("HTTPSampler.protocol", "http")
                .child(userParams)
                .build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 1)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(httpSampler)
                .build();

        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        TestRunContext ctx = TestRunContext.builder()
                .runId("user-params-test")
                .planPath("test.jmx")
                .virtualUsers(1)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        TestRunResult result = interpreter.execute(plan, ctx);

        assertEquals(1, result.totalSamples());
        assertTrue(client.requestedUrls.stream().anyMatch(u -> u.contains("/api/users")),
                "HTTP sampler should use the variable set by UserParameters. URLs: "
                        + client.requestedUrls);
    }

    @Test
    void onceOnlyController_executesOnlyOnFirstIteration() throws Exception {
        PlanNode debugInOnce = PlanNode.builder("DebugSampler", "Login").build();

        PlanNode onceOnly = PlanNode.builder("OnceOnlyController", "Once Login")
                .child(debugInOnce)
                .build();

        PlanNode regularSampler = PlanNode.builder("DebugSampler", "Regular").build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 3)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(onceOnly)
                .child(regularSampler)
                .build();

        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        TestRunContext ctx = TestRunContext.builder()
                .runId("once-only-test")
                .planPath("test.jmx")
                .virtualUsers(1)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        TestRunResult result = interpreter.execute(plan, ctx);

        // 3 loops: 1st loop = Login + Regular, 2nd = Regular only, 3rd = Regular only
        // Total = 1 (once) + 3 (regular) = 4
        assertEquals(4, result.totalSamples(),
                "OnceOnly should execute 1 time + 3 regular = 4 total samples");
    }

    @Test
    void forEachController_iteratesOverVariables_noScript() throws Exception {
        // Use UserParameters to set up indexed variables, then ForEach iterates
        PlanNode userParams = PlanNode.builder("UserParameters", "Set URLs")
                .property("UserParameters.names", List.of("urls_1", "urls_2"))
                .property("UserParameters.thread_values",
                        List.of(List.of("/page1", "/page2")))
                .build();

        // Setup sampler has the UserParameters pre-processor as child
        PlanNode setupSampler = PlanNode.builder("DebugSampler", "Setup")
                .child(userParams)
                .build();

        // ForEach iterates over urls_1, urls_2
        PlanNode innerSampler = PlanNode.builder("DebugSampler", "Process URL").build();

        PlanNode forEach = PlanNode.builder("ForeachController", "ForEach URLs")
                .property("ForeachController.inputVal", "urls")
                .property("ForeachController.returnVal", "currentUrl")
                .property("ForeachController.useSeparator", true)
                .child(innerSampler)
                .build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 1)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(setupSampler)
                .child(forEach)
                .build();

        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        TestRunContext ctx = TestRunContext.builder()
                .runId("foreach-test")
                .planPath("test.jmx")
                .virtualUsers(1)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        TestRunResult result = interpreter.execute(plan, ctx);

        // 1 (setup) + 2 (forEach iterations) = 3
        assertEquals(3, result.totalSamples(),
                "Should have 1 setup + 2 forEach iterations = 3 samples");
    }

    @Test
    void throughputController_totalMode_limitsExecution() throws Exception {
        PlanNode debugSampler = PlanNode.builder("DebugSampler", "Limited").build();

        PlanNode throughput = PlanNode.builder("ThroughputController", "Max 1")
                .property("ThroughputController.style", 1) // Total mode
                .property("ThroughputController.maxThroughput", 1)
                .property("ThroughputController.perThread", false)
                .child(debugSampler)
                .build();

        PlanNode regularSampler = PlanNode.builder("DebugSampler", "Always").build();

        PlanNode loopCtrl = PlanNode.builder("LoopController", "ctrl")
                .property("LoopController.loops", 3)
                .build();

        PlanNode tg = PlanNode.builder("ThreadGroup", "Users")
                .property("ThreadGroup.num_threads", 1)
                .property("ThreadGroup.main_controller", loopCtrl)
                .child(throughput)
                .child(regularSampler)
                .build();

        PlanNode plan = PlanNode.builder("TestPlan", "Plan").child(tg).build();

        // Reset counters since ThroughputController uses static state
        ThroughputControllerExecutor.resetCounters();

        NodeInterpreter interpreter = StubInterpreterFactory.create();
        TestRunContext ctx = TestRunContext.builder()
                .runId("throughput-test")
                .planPath("test.jmx")
                .virtualUsers(1)
                .uiBridge(StubInterpreterFactory.noOpUiBridge())
                .build();

        TestRunResult result = interpreter.execute(plan, ctx);

        // 3 loops: throughput executes 1 time total, regular executes 3 times
        // Total = 1 (throughput) + 3 (regular) = 4
        assertEquals(4, result.totalSamples(),
                "Throughput controller (max 1) + 3 regular = 4 total samples");
    }

    // =========================================================================
    // Recording HTTP client
    // =========================================================================

    private static final class RecordingClient implements HttpClientFactory {
        final List<String> requestedUrls = new CopyOnWriteArrayList<>();

        @Override
        public HttpResponse execute(HttpRequest request) {
            requestedUrls.add(request.url());
            return new HttpResponse(200, "HTTP/1.1", Map.of(),
                    "OK".getBytes(StandardCharsets.UTF_8),
                    0L, 1L, 1L, false);
        }

        @Override
        public void close() {}
    }
}
