package com.jmeternext.engine.service.plan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes a {@link PlanNode} tree back to JMX XML format.
 *
 * <h2>Round-trip guarantee</h2>
 * A plan parsed by {@link JmxTreeWalker} and then written by this class produces
 * XML that, when re-parsed, yields a structurally equivalent {@link PlanNode} tree
 * (same testClass, testName, property keys/values, and child ordering).  The
 * output XML may differ in whitespace or attribute ordering from the original
 * source, but the semantic content is preserved.
 *
 * <h2>Output format</h2>
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <jmeterTestPlan version="1.2" properties="5.0" jmeter="6.0">
 *   <hashTree>
 *     <ThreadGroup testclass="ThreadGroup" testname="Users">
 *       <intProp name="ThreadGroup.num_threads">10</intProp>
 *     </ThreadGroup>
 *     <hashTree>
 *       ...
 *     </hashTree>
 *   </hashTree>
 * </jmeterTestPlan>
 * }</pre>
 *
 * <p>This class has zero dependency on old jMeter classes — it uses only
 * plain Java string building.
 */
public final class JmxWriter {

    private static final String INDENT = "  ";
    private static final String JMETER_TEST_PLAN = "jmeterTestPlan";

    private JmxWriter() {
        // utility class — not instantiable
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Serializes the {@link PlanNode} tree rooted at {@code root} to JMX XML.
     *
     * @param root the root node (should be a {@code jmeterTestPlan} node from
     *             {@link JmxTreeWalker}, but any node is accepted); must not be
     *             {@code null}
     * @return well-formed XML string; never {@code null}
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static String write(PlanNode root) {
        Objects.requireNonNull(root, "root must not be null");
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        if (JMETER_TEST_PLAN.equals(root.getTestClass())) {
            // Write the wrapper element with version attributes
            String version = root.getStringProp("version", "1.2");
            sb.append("<jmeterTestPlan version=\"")
              .append(escapeXml(version))
              .append("\" properties=\"5.0\" jmeter=\"6.0\">\n");
            sb.append(INDENT).append("<hashTree>\n");
            for (PlanNode child : root.getChildren()) {
                writeNode(sb, child, 2);
            }
            sb.append(INDENT).append("</hashTree>\n");
            sb.append("</jmeterTestPlan>\n");
        } else {
            // Non-standard root — write as a regular node
            writeNode(sb, root, 0);
        }

        return sb.toString();
    }

    // =========================================================================
    // Node serialization
    // =========================================================================

    /**
     * Writes a single node and its children at the given indent depth.
     *
     * <p>Each node is followed by a {@code <hashTree>} block containing its
     * children (or an empty {@code <hashTree/>} if it has none).
     */
    private static void writeNode(StringBuilder sb, PlanNode node, int depth) {
        String pad = indent(depth);

        // Opening tag with testclass and testname attributes
        sb.append(pad).append('<').append(escapeXml(node.getTestClass()));
        sb.append(" testclass=\"").append(escapeXml(node.getTestClass())).append('"');
        sb.append(" testname=\"").append(escapeXml(node.getTestName())).append('"');

        // guiclass if present
        Object guiClass = node.getProperties().get("guiclass");
        if (guiClass instanceof String) {
            sb.append(" guiclass=\"").append(escapeXml((String) guiClass)).append('"');
        }

        sb.append(">\n");

        // Write properties (except internal ones like "guiclass", "version")
        writeProperties(sb, node, depth + 1);

        sb.append(pad).append("</").append(escapeXml(node.getTestClass())).append(">\n");

        // hashTree for children
        if (node.getChildren().isEmpty()) {
            sb.append(pad).append("<hashTree/>\n");
        } else {
            sb.append(pad).append("<hashTree>\n");
            for (PlanNode child : node.getChildren()) {
                writeNode(sb, child, depth + 1);
            }
            sb.append(pad).append("</hashTree>\n");
        }
    }

    /**
     * Writes all typed properties of {@code node} as child XML elements,
     * skipping internal properties ({@code guiclass}).
     */
    private static void writeProperties(StringBuilder sb, PlanNode node, int depth) {
        String pad = indent(depth);
        for (Map.Entry<String, Object> entry : node.getProperties().entrySet()) {
            String name  = entry.getKey();
            Object value = entry.getValue();

            // Skip internal bookkeeping properties
            if ("guiclass".equals(name) || "version".equals(name)) {
                continue;
            }

            if (value instanceof String) {
                sb.append(pad)
                  .append("<stringProp name=\"").append(escapeXml(name)).append("\">")
                  .append(escapeXml((String) value))
                  .append("</stringProp>\n");

            } else if (value instanceof Integer) {
                sb.append(pad)
                  .append("<intProp name=\"").append(escapeXml(name)).append("\">")
                  .append(value)
                  .append("</intProp>\n");

            } else if (value instanceof Long) {
                sb.append(pad)
                  .append("<longProp name=\"").append(escapeXml(name)).append("\">")
                  .append(value)
                  .append("</longProp>\n");

            } else if (value instanceof Boolean) {
                sb.append(pad)
                  .append("<boolProp name=\"").append(escapeXml(name)).append("\">")
                  .append(value)
                  .append("</boolProp>\n");

            } else if (value instanceof Double) {
                sb.append(pad)
                  .append("<doubleProp name=\"").append(escapeXml(name)).append("\">")
                  .append(value)
                  .append("</doubleProp>\n");

            } else if (value instanceof PlanNode) {
                writeElementProp(sb, name, (PlanNode) value, depth);

            } else if (value instanceof List) {
                writeCollectionProp(sb, name, (List<?>) value, depth);
            }
            // null or unknown types are silently skipped
        }
    }

    /**
     * Writes an {@code <elementProp>} block for a nested {@link PlanNode}.
     */
    private static void writeElementProp(StringBuilder sb, String propName, PlanNode nested, int depth) {
        String pad = indent(depth);
        sb.append(pad)
          .append("<elementProp name=\"").append(escapeXml(propName)).append('"')
          .append(" elementType=\"").append(escapeXml(nested.getTestClass())).append("\">\n");
        writeProperties(sb, nested, depth + 1);
        sb.append(pad).append("</elementProp>\n");
    }

    /**
     * Writes a {@code <collectionProp>} block for a list of items.
     */
    private static void writeCollectionProp(StringBuilder sb, String propName, List<?> items, int depth) {
        String pad = indent(depth);
        sb.append(pad)
          .append("<collectionProp name=\"").append(escapeXml(propName)).append("\">\n");
        for (Object item : items) {
            if (item instanceof PlanNode) {
                PlanNode node = (PlanNode) item;
                writeElementProp(sb, node.getTestName(), node, depth + 1);
            } else if (item instanceof String) {
                sb.append(indent(depth + 1))
                  .append("<stringProp>").append(escapeXml((String) item)).append("</stringProp>\n");
            } else if (item instanceof Integer) {
                sb.append(indent(depth + 1))
                  .append("<intProp>").append(item).append("</intProp>\n");
            } else if (item instanceof Boolean) {
                sb.append(indent(depth + 1))
                  .append("<boolProp>").append(item).append("</boolProp>\n");
            }
            // null items and other types silently skipped
        }
        sb.append(pad).append("</collectionProp>\n");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String indent(int depth) {
        if (depth <= 0) return "";
        StringBuilder sb = new StringBuilder(depth * INDENT.length());
        for (int i = 0; i < depth; i++) sb.append(INDENT);
        return sb.toString();
    }

    /**
     * Escapes the five standard XML entities in a text value.
     *
     * @param text input, may be {@code null}
     * @return escaped string, or empty string for {@code null} input
     */
    static String escapeXml(String text) {
        if (text == null) return "";
        // Use indexOf to avoid creating a new string when nothing needs escaping
        if (text.indexOf('&') < 0 && text.indexOf('<') < 0 && text.indexOf('>') < 0
                && text.indexOf('"') < 0 && text.indexOf('\'') < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}
