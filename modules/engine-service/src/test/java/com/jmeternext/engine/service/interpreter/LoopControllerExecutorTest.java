package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.http.HttpRequest;
import com.jmeternext.engine.service.http.HttpResponse;
import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LoopControllerExecutor}.
 *
 * <p>Uses a counting {@link HttpClientFactory} stub to verify that the loop
 * controller executes children the correct number of times without requiring
 * network infrastructure.
 */
class LoopControllerExecutorTest {

    @Test
    void zeroLoops_producesNoResults() {
        CountingHttpClient client = new CountingHttpClient();
        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);

        PlanNode httpChild = httpChild();
        PlanNode loop = loopNode(0, httpChild);

        List<SampleResult> results = exec.execute(loop, new HashMap<>());

        assertTrue(results.isEmpty(), "0 loops should produce no results");
        assertEquals(0, client.callCount(), "HTTP client should not be called for 0 loops");
    }

    @Test
    void oneLoop_executesChildrenOnce() {
        CountingHttpClient client = new CountingHttpClient();
        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);

        PlanNode loop = loopNode(1, httpChild());

        exec.execute(loop, new HashMap<>());

        assertEquals(1, client.callCount(), "HTTP client should be called exactly once");
    }

    @Test
    void fiveLoops_executesFiveTimes() {
        CountingHttpClient client = new CountingHttpClient();
        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);

        PlanNode loop = loopNode(5, httpChild());

        List<SampleResult> results = exec.execute(loop, new HashMap<>());

        assertEquals(5, client.callCount(), "HTTP client should be called 5 times");
        assertEquals(5, results.size(), "5 loops of 1 HTTP node should produce 5 samples");
    }

    @Test
    void threeLoops_correctResultCount() {
        CountingHttpClient client = new CountingHttpClient();
        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);

        PlanNode loop = loopNode(3, httpChild());

        List<SampleResult> results = exec.execute(loop, new HashMap<>());

        assertEquals(3, results.size(), "3 loops of 1 HTTP node should produce 3 SampleResults");
        // All should be successful (stub returns 200)
        assertTrue(results.stream().allMatch(SampleResult::isSuccess));
    }

    @Test
    void allResultsHaveSamplerLabel() {
        CountingHttpClient client = new CountingHttpClient();
        NodeInterpreter interpreter = new NodeInterpreter(client, StubInterpreterFactory.noOpBroker());
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);

        PlanNode loop = loopNode(2, httpChild());
        List<SampleResult> results = exec.execute(loop, new HashMap<>());

        assertTrue(results.stream().allMatch(r -> "req".equals(r.getLabel())),
                "All results should carry the sampler label");
    }

    @Test
    void nullNode_throws() {
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);
        assertThrows(NullPointerException.class, () -> exec.execute(null, new HashMap<>()));
    }

    @Test
    void nullVariables_throws() {
        PlanNode loop = loopNode(1, httpChild());
        NodeInterpreter interpreter = StubInterpreterFactory.create();
        LoopControllerExecutor exec = new LoopControllerExecutor(interpreter);
        assertThrows(NullPointerException.class, () -> exec.execute(loop, null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static PlanNode loopNode(int loops, PlanNode... children) {
        PlanNode.Builder b = PlanNode.builder("LoopController", "loop")
                .property("LoopController.loops", loops);
        for (PlanNode child : children) {
            b.child(child);
        }
        return b.build();
    }

    private static PlanNode httpChild() {
        return PlanNode.builder("HTTPSamplerProxy", "req")
                .property("HTTPSampler.domain",   "example.com")
                .property("HTTPSampler.path",     "/")
                .property("HTTPSampler.method",   "GET")
                .property("HTTPSampler.protocol", "http")
                .build();
    }

    // -------------------------------------------------------------------------
    // Counting stub
    // -------------------------------------------------------------------------

    private static final class CountingHttpClient implements HttpClientFactory {

        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public HttpResponse execute(HttpRequest request) throws IOException {
            count.incrementAndGet();
            return new HttpResponse(200, "HTTP/1.1", Map.of(), new byte[0],
                    0L, 1L, 1L, false);
        }

        @Override
        public void close() {}

        int callCount() {
            return count.get();
        }
    }
}
