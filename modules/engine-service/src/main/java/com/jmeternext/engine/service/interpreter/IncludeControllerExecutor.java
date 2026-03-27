package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.JmxParseException;
import com.jmeternext.engine.service.plan.JmxTreeWalker;
import com.jmeternext.engine.service.plan.PlanNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code IncludeController} {@link PlanNode}.
 *
 * <p>The Include Controller loads an external JMX file and executes its
 * test plan elements. The path to the external file is stored in the
 * {@code IncludeController.includePath} property.
 *
 * <p>At execution time, the executor reads the JMX file, parses it with
 * {@link JmxTreeWalker}, and executes the resulting tree's children.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class IncludeControllerExecutor {

    private static final Logger LOG = Logger.getLogger(IncludeControllerExecutor.class.getName());

    private final NodeInterpreter interpreter;

    /**
     * Constructs an {@code IncludeControllerExecutor}.
     *
     * @param interpreter the parent interpreter for executing child nodes; must not be {@code null}
     */
    public IncludeControllerExecutor(NodeInterpreter interpreter) {
        this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    }

    /**
     * Executes the include controller by loading and parsing the external JMX
     * file, then executing its children.
     *
     * @param node      the IncludeController node; must not be {@code null}
     * @param variables mutable VU variable map
     * @return list of {@link SampleResult}s from the included plan's execution
     */
    public List<SampleResult> execute(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String includePath = node.getStringProp("IncludeController.includePath", "");

        if (includePath.isEmpty()) {
            LOG.log(Level.WARNING,
                    "IncludeControllerExecutor [{0}]: empty includePath — skipping",
                    node.getTestName());
            return List.of();
        }

        Path path = Paths.get(includePath);
        if (!Files.exists(path)) {
            LOG.log(Level.WARNING,
                    "IncludeControllerExecutor [{0}]: file not found: {1} — skipping",
                    new Object[]{node.getTestName(), includePath});
            return List.of();
        }

        try (InputStream is = new FileInputStream(path.toFile())) {
            PlanNode includedRoot = JmxTreeWalker.parse(is);

            // Walk to the TestPlan children (same logic as NodeInterpreter)
            List<PlanNode> children = resolveIncludedChildren(includedRoot);

            LOG.log(Level.FINE,
                    "IncludeControllerExecutor [{0}]: loaded {1} with {2} children",
                    new Object[]{node.getTestName(), includePath, children.size()});

            return interpreter.executeChildren(children, variables);

        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "IncludeControllerExecutor [{0}]: I/O error reading {1}: {2}",
                    new Object[]{node.getTestName(), includePath, e.getMessage()});
            return List.of();
        } catch (JmxParseException e) {
            LOG.log(Level.WARNING,
                    "IncludeControllerExecutor [{0}]: parse error in {1}: {2}",
                    new Object[]{node.getTestName(), includePath, e.getMessage()});
            return List.of();
        }
    }

    /**
     * Resolves the children to execute from an included plan root.
     * Walks into TestPlan if present, or uses direct children.
     */
    private static List<PlanNode> resolveIncludedChildren(PlanNode root) {
        // Check for TestPlan as direct child
        for (PlanNode child : root.getChildren()) {
            if ("TestPlan".equals(child.getTestClass())) {
                return child.getChildren();
            }
        }
        // Root is TestPlan itself
        if ("TestPlan".equals(root.getTestClass())) {
            return root.getChildren();
        }
        return root.getChildren();
    }
}
