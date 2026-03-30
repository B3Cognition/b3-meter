package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code AjpSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties (matching JMeter's AJP/1.3 Sampler):
 * <ul>
 *   <li>{@code HTTPSampler.domain} — AJP server hostname</li>
 *   <li>{@code HTTPSampler.port} — AJP port (default 8009)</li>
 *   <li>{@code HTTPSampler.path} — request URI path</li>
 *   <li>{@code HTTPSampler.method} — HTTP method (default GET)</li>
 *   <li>{@code HTTPSampler.protocol} — protocol (default http)</li>
 *   <li>{@code HTTPSampler.connect_timeout} — connect timeout in ms</li>
 *   <li>{@code HTTPSampler.response_timeout} — response timeout in ms</li>
 * </ul>
 *
 * <p>This is a STUB implementation. AJP 1.3 is a binary protocol (Apache mod_jk)
 * that requires a custom binary codec. The stub parses all JMX properties so
 * imported test plans do not crash, and returns a 501 (Not Implemented) result.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class AJPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(AJPSamplerExecutor.class.getName());

    private static final int DEFAULT_AJP_PORT = 8009;

    private AJPSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the AJP sampler described by {@code node}.
     *
     * @param node      the AjpSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String domain = resolve(node.getStringProp("HTTPSampler.domain", ""), variables);
        int port = node.getIntProp("HTTPSampler.port", DEFAULT_AJP_PORT);
        String path = resolve(node.getStringProp("HTTPSampler.path", "/"), variables);
        String method = resolve(node.getStringProp("HTTPSampler.method", "GET"), variables);
        String protocol = resolve(node.getStringProp("HTTPSampler.protocol", "http"), variables);
        int connectTimeout = node.getIntProp("HTTPSampler.connect_timeout", 0);
        int responseTimeout = node.getIntProp("HTTPSampler.response_timeout", 0);

        if (domain.isBlank()) {
            result.setFailureMessage("AJPSampler: HTTPSampler.domain is empty");
            return;
        }

        LOG.log(Level.WARNING,
                "AJPSamplerExecutor: AJP 1.3 protocol not implemented. "
                + "Domain={0}:{1}, Path={2}, Method={3}",
                new Object[]{domain, port, path, method});

        result.setStatusCode(501);
        result.setResponseBody(
                "AJP 1.3 Sampler is a stub. The AJP binary protocol is not implemented.\n"
                + "Domain: " + domain + "\n"
                + "Port: " + port + "\n"
                + "Path: " + path + "\n"
                + "Method: " + method + "\n"
                + "Protocol: " + protocol + "\n"
                + "Connect Timeout: " + connectTimeout + "ms\n"
                + "Response Timeout: " + responseTimeout + "ms");
        result.setFailureMessage("AJP 1.3 protocol not implemented");
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
