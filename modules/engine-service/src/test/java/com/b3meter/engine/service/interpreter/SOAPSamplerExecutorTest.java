package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SOAPSamplerExecutor}.
 */
class SOAPSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("soap");
        assertThrows(NullPointerException.class,
                () -> SOAPSamplerExecutor.execute(null, r, Map.of(), null));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = soapNode("example.com", "<soap/>", "urn:test");
        assertThrows(NullPointerException.class,
                () -> SOAPSamplerExecutor.execute(node, null, Map.of(), null));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void failsOnEmptyDomain() {
        PlanNode node = PlanNode.builder("SOAPSampler", "soap-empty-domain")
                .property("SOAPSampler.xml_data", "<soap/>")
                .build();

        SampleResult result = new SampleResult("soap-empty-domain");
        SOAPSamplerExecutor.execute(node, result, Map.of(),
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("domain is empty"));
    }

    @Test
    void failsOnEmptyXmlData() {
        PlanNode node = PlanNode.builder("SOAPSampler", "soap-empty-xml")
                .property("HTTPSampler.domain", "example.com")
                .build();

        SampleResult result = new SampleResult("soap-empty-xml");
        SOAPSamplerExecutor.execute(node, result, Map.of(),
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("xml_data is empty"));
    }

    @Test
    void resolvesVariables() {
        PlanNode node = PlanNode.builder("SOAPSampler", "soap-vars")
                .property("HTTPSampler.domain", "${host}")
                .property("SOAPSampler.xml_data", "<env>${body}</env>")
                .property("SOAPSampler.SOAP_ACTION", "${action}")
                .build();

        Map<String, String> vars = new HashMap<>();
        vars.put("host", ""); // empty after resolution -> should fail
        vars.put("body", "test");
        vars.put("action", "urn:test");

        SampleResult result = new SampleResult("soap-vars");
        SOAPSamplerExecutor.execute(node, result, vars,
                StubInterpreterFactory.noOpHttpClient());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("domain is empty"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode soapNode(String domain, String xmlData, String soapAction) {
        return PlanNode.builder("SOAPSampler", "soap-test")
                .property("HTTPSampler.domain", domain)
                .property("SOAPSampler.xml_data", xmlData)
                .property("SOAPSampler.SOAP_ACTION", soapAction)
                .build();
    }
}
