package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code RandomOrderController} {@link PlanNode}.
 *
 * <p>Executes ALL children but in a randomized order each iteration. Unlike
 * {@link RandomControllerExecutor} which picks one child, this executes all
 * children with their order shuffled.
 *
 * <p>No configurable properties beyond Name and Comments.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class RandomOrderControllerExecutor {

    private static final Logger LOG = Logger.getLogger(RandomOrderControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code RandomOrderControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public RandomOrderControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Shuffles the children list and executes all children in the randomized order.
     *
     * @param node      the RandomOrderController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of all {@link SampleResult}s produced from all children
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<PlanNode> children = node.getChildren();
        if (children.isEmpty()) {
            LOG.log(Level.FINE,
                    "RandomOrderControllerExecutor [{0}]: no children — skipping",
                    node.getTestName());
            return List.of();
        }

        // Create a mutable copy and shuffle
        List<PlanNode> shuffled = new ArrayList<>(children);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        LOG.log(Level.FINE,
                "RandomOrderControllerExecutor [{0}]: executing {1} children in random order",
                new Object[]{node.getTestName(), children.size()});

        return interpreter.executeChildren(shuffled, variables);
    }
}
