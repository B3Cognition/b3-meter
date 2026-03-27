package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Executes an {@code XPathAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Reads the standard JMeter XPathAssertion properties:
 * <ul>
 *   <li>{@code XPath.xpath} — XPath expression to evaluate</li>
 *   <li>{@code XPath.negate} — if true, invert the assertion result</li>
 *   <li>{@code XPath.tolerant} — if true, use tolerant parsing</li>
 *   <li>{@code XPath.namespace} — if true, enable namespace processing</li>
 * </ul>
 *
 * <p>Uses {@code javax.xml.xpath.XPathFactory} (built into JDK).
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class XPathAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(XPathAssertionExecutor.class.getName());

    private XPathAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the XPath assertion described by {@code node} to {@code result}.
     *
     * @param node      the XPathAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String xpathExpr = VariableResolver.resolve(node.getStringProp("XPath.xpath", ""), variables);
        boolean negate    = node.getBoolProp("XPath.negate");
        boolean namespace = node.getBoolProp("XPath.namespace");

        if (xpathExpr.isBlank()) {
            result.setFailureMessage("XPathAssertion [" + node.getTestName() + "]: XPath expression is empty");
            return;
        }

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            applyResult(result, node.getTestName(), false, negate,
                    "Response body is empty — cannot evaluate XPath");
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(namespace);
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(body)));

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            // Try boolean evaluation first
            try {
                Boolean boolResult = (Boolean) xpath.evaluate(xpathExpr, doc, XPathConstants.BOOLEAN);
                applyResult(result, node.getTestName(), boolResult, negate,
                        boolResult ? null : "XPath '" + xpathExpr + "' evaluated to false");
            } catch (Exception e) {
                // Fall back to nodeset evaluation
                NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                boolean found = nodes != null && nodes.getLength() > 0;
                applyResult(result, node.getTestName(), found, negate,
                        found ? null : "XPath '" + xpathExpr + "' matched 0 nodes");
            }

        } catch (Exception e) {
            applyResult(result, node.getTestName(), false, negate,
                    "XML/XPath error: " + e.getMessage());
            LOG.log(Level.FINE, "XPathAssertion [{0}]: error", new Object[]{node.getTestName()});
        }
    }

    private static void applyResult(SampleResult result, String testName,
                                     boolean passed, boolean negate, String message) {
        boolean finalResult = negate ? !passed : passed;
        if (!finalResult) {
            String prefix = negate ? " (negated)" : "";
            String msg = "XPathAssertion [" + testName + "]" + prefix + " FAILED"
                    + (message != null ? ": " + message : "");
            result.setFailureMessage(msg);
        }
    }
}
