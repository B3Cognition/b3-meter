package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code ModuleController} {@link PlanNode}.
 *
 * <p>The Module Controller references another controller by path in the test
 * plan tree and executes its children. The path is stored as a collection
 * property {@code ModuleController.node_path} containing the tree path
 * segments from the root to the target controller.
 *
 * <p>At execution time, the executor resolves the path by walking the plan
 * tree from the given root, then delegates execution of the found node's
 * children to the interpreter.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class ModuleControllerExecutor {

    private static final Logger LOG = Logger.getLogger(ModuleControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs a {@code ModuleControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public ModuleControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes the module controller by resolving the target node path
     * and executing the target's children.
     *
     * @param node      the ModuleController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @param root      the root of the entire plan tree for path resolution
     * @return list of {@link SampleResult}s from the target's children
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables,
                                       PlanNode root) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        List<Object> nodePath = node.getCollectionProp("ModuleController.node_path");

        if (nodePath.isEmpty()) {
            LOG.log(Level.WARNING,
                    "ModuleControllerExecutor [{0}]: empty node_path — skipping",
                    node.getTestName());
            return List.of();
        }

        PlanNode target = resolveNodePath(root, nodePath);

        if (target == null) {
            LOG.log(Level.WARNING,
                    "ModuleControllerExecutor [{0}]: could not resolve path {1} — skipping",
                    new Object[]{node.getTestName(), nodePath});
            return List.of();
        }

        LOG.log(Level.FINE,
                "ModuleControllerExecutor [{0}]: executing target [{1}] with {2} children",
                new Object[]{node.getTestName(), target.getTestName(), target.getChildren().size()});

        return interpreter.executeChildren(target.getChildren(), variables);
    }

    /**
     * Resolves a node path through the plan tree.
     *
     * <p>The path segments correspond to test element names, starting from the root.
     * Each segment is matched against children of the current node by name.
     *
     * @param root     the root node to start from
     * @param nodePath list of path segment names
     * @return the resolved node, or {@code null} if not found
     */
    static PlanNode resolveNodePath(PlanNode root, List<Object> nodePath) {
        if (root == null || nodePath.isEmpty()) return null;

        PlanNode current = root;

        // The first segment typically matches the root (TestPlan); skip it
        // if the root name matches. Otherwise start from the first segment.
        int startIdx = 0;
        if (!nodePath.isEmpty() && root.getTestName() != null
                && root.getTestName().equals(String.valueOf(nodePath.get(0)))) {
            startIdx = 1;
        }

        for (int i = startIdx; i < nodePath.size(); i++) {
            String segmentName = String.valueOf(nodePath.get(i));
            PlanNode found = null;
            for (PlanNode child : current.getChildren()) {
                if (segmentName.equals(child.getTestName())) {
                    found = child;
                    break;
                }
            }
            if (found == null) {
                // Try descending into intermediate nodes (e.g., TestPlan wrapper)
                for (PlanNode child : current.getChildren()) {
                    PlanNode deeper = findByNameRecursive(child, segmentName);
                    if (deeper != null) {
                        found = deeper;
                        break;
                    }
                }
            }
            if (found == null) {
                return null;
            }
            current = found;
        }
        return current;
    }

    private static PlanNode findByNameRecursive(PlanNode node, String name) {
        if (name.equals(node.getTestName())) return node;
        for (PlanNode child : node.getChildren()) {
            PlanNode found = findByNameRecursive(child, name);
            if (found != null) return found;
        }
        return null;
    }
}
