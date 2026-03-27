package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AJPSamplerExecutor}.
 */
class AJPSamplerExecutorTest {

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("ajp");
        assertThrows(NullPointerException.class,
                () -> AJPSamplerExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = ajpNode("tomcat.local");
        assertThrows(NullPointerException.class,
                () -> AJPSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = ajpNode("tomcat.local");
        SampleResult r = new SampleResult("ajp");
        assertThrows(NullPointerException.class,
                () -> AJPSamplerExecutor.execute(node, r, null));
    }

    @Test
    void failsOnEmptyDomain() {
        PlanNode node = PlanNode.builder("AjpSampler", "ajp-empty")
                .build();

        SampleResult result = new SampleResult("ajp-empty");
        AJPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("domain is empty"));
    }

    @Test
    void returns501Stub() {
        PlanNode node = PlanNode.builder("AjpSampler", "ajp-stub")
                .property("HTTPSampler.domain", "tomcat.local")
                .property("HTTPSampler.port", "8009")
                .property("HTTPSampler.path", "/app/test")
                .property("HTTPSampler.method", "GET")
                .build();

        SampleResult result = new SampleResult("ajp-stub");
        AJPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(501, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("AJP 1.3"));
        assertTrue(result.getResponseBody().contains("tomcat.local"));
        assertTrue(result.getResponseBody().contains("8009"));
        assertTrue(result.getResponseBody().contains("/app/test"));
    }

    @Test
    void resolvesVariables() {
        PlanNode node = PlanNode.builder("AjpSampler", "ajp-vars")
                .property("HTTPSampler.domain", "${ajp_host}")
                .property("HTTPSampler.path", "${ajp_path}")
                .build();

        Map<String, String> vars = new HashMap<>();
        vars.put("ajp_host", "resolved.tomcat");
        vars.put("ajp_path", "/resolved");

        SampleResult result = new SampleResult("ajp-vars");
        AJPSamplerExecutor.execute(node, result, vars);

        assertTrue(result.getResponseBody().contains("resolved.tomcat"));
        assertTrue(result.getResponseBody().contains("/resolved"));
    }

    private static PlanNode ajpNode(String domain) {
        return PlanNode.builder("AjpSampler", "ajp-test")
                .property("HTTPSampler.domain", domain)
                .build();
    }
}
