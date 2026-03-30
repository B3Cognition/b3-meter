package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code SwitchController} {@link PlanNode}.
 *
 * <p>Evaluates a switch expression and selects exactly one child to execute.
 * The value can be a numeric 0-based index into the children list, or a
 * string matching a child's testName. If no match is found, the first child
 * (index 0) is executed as the default case.
 *
 * <p>Reads the following properties:
 * <ul>
 *   <li>{@code SwitchController.value} — switch expression (numeric index or child name)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SwitchControllerExecutor {

    private static final Logger LOG = Logger.getLogger(SwitchControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code SwitchControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public SwitchControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Evaluates the switch value and executes the matching child.
     *
     * @param node      the SwitchController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from the selected child
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<PlanNode> children = node.getChildren();
        if (children.isEmpty()) {
            LOG.log(Level.FINE,
                    "SwitchControllerExecutor [{0}]: no children — skipping",
                    node.getTestName());
            return List.of();
        }

        String rawValue = node.getStringProp("SwitchController.value", "0");
        // Resolve variable references
        String value = VariableResolver.resolve(rawValue, variables);

        // Try numeric index first
        try {
            int index = Integer.parseInt(value.trim());
            if (index >= 0 && index < children.size()) {
                LOG.log(Level.FINE,
                        "SwitchControllerExecutor [{0}]: executing child at index {1}",
                        new Object[]{node.getTestName(), index});
                return interpreter.executeChildren(List.of(children.get(index)), variables);
            }
        } catch (NumberFormatException ignored) {
            // Not numeric — try name match
        }

        // Try name match
        for (PlanNode child : children) {
            if (value.equals(child.getTestName())) {
                LOG.log(Level.FINE,
                        "SwitchControllerExecutor [{0}]: executing child by name '{1}'",
                        new Object[]{node.getTestName(), value});
                return interpreter.executeChildren(List.of(child), variables);
            }
        }

        // Default: execute first child
        LOG.log(Level.FINE,
                "SwitchControllerExecutor [{0}]: no match for '{1}' — executing default (index 0)",
                new Object[]{node.getTestName(), value});
        return interpreter.executeChildren(List.of(children.get(0)), variables);
    }
}
