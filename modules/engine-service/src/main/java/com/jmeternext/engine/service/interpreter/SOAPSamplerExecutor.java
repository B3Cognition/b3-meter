package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.http.HttpClientFactory;
import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code SOAPSampler} or {@code SOAPSampler2} {@link PlanNode} by delegating
 * to {@link HttpSamplerExecutor} with SOAP-specific headers.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code HTTPSampler.domain} — SOAP endpoint hostname</li>
 *   <li>{@code HTTPSampler.port} — endpoint port (default 80)</li>
 *   <li>{@code HTTPSampler.path} — endpoint path</li>
 *   <li>{@code HTTPSampler.protocol} — http or https</li>
 *   <li>{@code SOAPSampler.xml_data} — SOAP XML envelope body</li>
 *   <li>{@code SOAPSampler.SOAP_ACTION} — SOAPAction HTTP header value</li>
 *   <li>{@code HTTPSampler.connect_timeout} — connect timeout in ms</li>
 *   <li>{@code HTTPSampler.response_timeout} — response timeout in ms</li>
 * </ul>
 *
 * <p>Implemented as an HTTP POST with Content-Type: text/xml and SOAPAction header,
 * delegating to the existing {@link HttpSamplerExecutor}.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SOAPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(SOAPSamplerExecutor.class.getName());

    private SOAPSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the SOAP request described by {@code node}.
     *
     * @param node              the SOAPSampler node; must not be {@code null}
     * @param result            the sample result to populate; must not be {@code null}
     * @param variables         current VU variable scope
     * @param httpClientFactory HTTP client factory for making the actual request
     */
    public static void execute(PlanNode node, SampleResult result,
                               Map<String, String> variables,
                               HttpClientFactory httpClientFactory) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");
        Objects.requireNonNull(httpClientFactory, "httpClientFactory must not be null");

        String xmlData = resolve(node.getStringProp("SOAPSampler.xml_data", ""), variables);
        String soapAction = resolve(node.getStringProp("SOAPSampler.SOAP_ACTION", ""), variables);
        String domain = resolve(node.getStringProp("HTTPSampler.domain", ""), variables);
        String path = resolve(node.getStringProp("HTTPSampler.path", "/"), variables);
        String protocol = resolve(node.getStringProp("HTTPSampler.protocol", "http"), variables);

        if (domain.isBlank()) {
            result.setFailureMessage("SOAPSampler: HTTPSampler.domain is empty");
            return;
        }

        if (xmlData.isBlank()) {
            result.setFailureMessage("SOAPSampler: SOAPSampler.xml_data is empty");
            return;
        }

        LOG.log(Level.FINE, "SOAPSamplerExecutor: POST to {0}://{1}{2} SOAPAction={3}",
                new Object[]{protocol, domain, path, soapAction});

        // Build a synthetic HTTP POST node with SOAP headers
        PlanNode.Builder builder = PlanNode.builder("HTTPSamplerProxy", node.getTestName())
                .property("HTTPSampler.domain", domain)
                .property("HTTPSampler.path", path)
                .property("HTTPSampler.method", "POST")
                .property("HTTPSampler.protocol", protocol)
                .property("HTTPSampler.postBodyRaw", xmlData);

        // Copy port if present
        String portStr = node.getStringProp("HTTPSampler.port", "");
        if (!portStr.isBlank()) {
            builder.property("HTTPSampler.port", portStr);
        }

        // Copy timeouts
        int connectTimeout = node.getIntProp("HTTPSampler.connect_timeout", 0);
        int responseTimeout = node.getIntProp("HTTPSampler.response_timeout", 0);
        if (connectTimeout > 0) {
            builder.property("HTTPSampler.connect_timeout", String.valueOf(connectTimeout));
        }
        if (responseTimeout > 0) {
            builder.property("HTTPSampler.response_timeout", String.valueOf(responseTimeout));
        }

        // Add SOAP headers via a child HeaderManager
        PlanNode headerNode = PlanNode.builder("HTTPHeaderManager", "SOAP Headers")
                .property("Header.Name.0", "Content-Type")
                .property("Header.Value.0", "text/xml; charset=utf-8")
                .property("Header.Name.1", "SOAPAction")
                .property("Header.Value.1", soapAction)
                .build();

        PlanNode httpNode = builder.child(headerNode).build();

        // Delegate to HttpSamplerExecutor
        HttpSamplerExecutor httpExecutor = new HttpSamplerExecutor(httpClientFactory);
        SampleResult httpResult = httpExecutor.execute(httpNode, variables);

        // Copy results
        result.setStatusCode(httpResult.getStatusCode());
        result.setResponseBody(httpResult.getResponseBody());
        result.setConnectTimeMs(httpResult.getConnectTimeMs());
        result.setLatencyMs(httpResult.getLatencyMs());
        result.setTotalTimeMs(httpResult.getTotalTimeMs());
        if (!httpResult.isSuccess()) {
            result.setFailureMessage(httpResult.getFailureMessage());
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
