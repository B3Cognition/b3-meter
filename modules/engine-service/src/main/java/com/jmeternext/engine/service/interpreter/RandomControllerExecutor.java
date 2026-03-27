package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code RandomController} {@link PlanNode}.
 *
 * <p>Randomly selects exactly ONE child to execute per iteration. A different
 * child may be chosen each time the controller is invoked.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RandomControllerExecutor {

    private static final Logger LOG = Logger.getLogger(RandomControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code RandomControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public RandomControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Randomly picks one child and executes it.
     *
     * @param node      the RandomController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from the selected child
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<PlanNode> children = node.getChildren();
        if (children.isEmpty()) {
            LOG.log(Level.FINE,
                    "RandomControllerExecutor [{0}]: no children — skipping",
                    node.getTestName());
            return List.of();
        }

        int index = ThreadLocalRandom.current().nextInt(children.size());

        LOG.log(Level.FINE,
                "RandomControllerExecutor [{0}]: selected child at index {1} of {2}",
                new Object[]{node.getTestName(), index, children.size()});

        return interpreter.executeChildren(List.of(children.get(index)), variables);
    }
}
