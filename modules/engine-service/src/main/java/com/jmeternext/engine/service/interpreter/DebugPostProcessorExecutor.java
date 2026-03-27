package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code DebugPostProcessor} {@link PlanNode} as a post-processor.
 *
 * <p>Dumps variables, properties, and/or sampler data to the response body of the
 * most recent sample result. Used for debugging variable state during test development.
 *
 * <p>Reads the standard JMeter DebugPostProcessor properties:
 * <ul>
 *   <li>{@code displayJMeterVariables} — if true, dump all VU variables</li>
 *   <li>{@code displayJMeterProperties} — if true, dump JMeter/system properties</li>
 *   <li>{@code displaySystemProperties} — if true, dump system properties</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class DebugPostProcessorExecutor {

    private static final Logger LOG = Logger.getLogger(DebugPostProcessorExecutor.class.getName());

    private DebugPostProcessorExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Appends debug information to the sample result's response body.
     *
     * @param node      the DebugPostProcessor node; must not be {@code null}
     * @param result    the most recent sample result; must not be {@code null}
     * @param variables current VU variable map
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean displayVars  = node.getBoolProp("displayJMeterVariables", true);
        boolean displayProps = node.getBoolProp("displayJMeterProperties");
        boolean displaySys   = node.getBoolProp("displaySystemProperties");

        StringBuilder sb = new StringBuilder();

        if (displayVars) {
            sb.append("[JMeter Variables]\n");
            // Sort for deterministic output
            new TreeMap<>(variables).forEach((k, v) ->
                    sb.append(k).append('=').append(v).append('\n'));
            sb.append('\n');
        }

        if (displayProps) {
            sb.append("[JMeter Properties]\n");
            Properties props = System.getProperties();
            new TreeMap<>(props).forEach((k, v) ->
                    sb.append(k).append('=').append(v).append('\n'));
            sb.append('\n');
        }

        if (displaySys) {
            sb.append("[System Properties]\n");
            Properties sysProps = System.getProperties();
            new TreeMap<>(sysProps).forEach((k, v) ->
                    sb.append(k).append('=').append(v).append('\n'));
            sb.append('\n');
        }

        String debugOutput = sb.toString();
        // Append to existing response body
        String existing = result.getResponseBody();
        if (existing != null && !existing.isEmpty()) {
            result.setResponseBody(existing + "\n\n--- Debug PostProcessor ---\n" + debugOutput);
        } else {
            result.setResponseBody(debugOutput);
        }

        LOG.log(Level.FINE, "DebugPostProcessor [{0}]: dumped {1} chars",
                new Object[]{node.getTestName(), debugOutput.length()});
    }
}
