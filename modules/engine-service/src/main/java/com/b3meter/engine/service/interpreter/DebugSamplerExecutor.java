package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Executes a {@code DebugSampler} {@link PlanNode}.
 *
 * <p>Collects and formats debug information as key=value pairs:
 * <ul>
 *   <li>JMeter variables (from the VU variable map)</li>
 *   <li>System properties (optional)</li>
 * </ul>
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code DebugSampler.displayJMeterVariables} — boolean, default {@code true}</li>
 *   <li>{@code DebugSampler.displaySystemProperties} — boolean, default {@code false}</li>
 * </ul>
 *
 * <p>Always succeeds. The collected debug dump is placed in the response body.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class DebugSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(DebugSamplerExecutor.class.getName());

    private DebugSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the debug sampler described by {@code node}.
     *
     * @param node      the DebugSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope; must not be {@code null}
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean displayVariables = node.getBoolProp("DebugSampler.displayJMeterVariables", true);
        boolean displaySysProps = node.getBoolProp("DebugSampler.displaySystemProperties", false);

        long start = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        if (displayVariables) {
            sb.append("=== JMeter Variables ===\n");
            if (variables.isEmpty()) {
                sb.append("(none)\n");
            } else {
                // Sort by key for deterministic output
                new TreeMap<>(variables).forEach((key, value) ->
                        sb.append(key).append('=').append(value).append('\n'));
            }
        }

        if (displaySysProps) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("=== System Properties ===\n");
            // Sort by key for deterministic output
            new TreeMap<>(System.getProperties()).forEach((key, value) ->
                    sb.append(key).append('=').append(value).append('\n'));
        }

        long total = System.currentTimeMillis() - start;
        result.setTotalTimeMs(total);
        result.setResponseBody(sb.toString());
        result.setStatusCode(200);
        // Debug sampler always succeeds
        result.setSuccess(true);
    }
}
