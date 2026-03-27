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
 * Executes an {@code XPathExtractor} {@link PlanNode} as a post-processor.
 *
 * <p>Reads the standard JMeter XPathExtractor properties:
 * <ul>
 *   <li>{@code XPathExtractor.xpathQuery} — XPath expression</li>
 *   <li>{@code XPathExtractor.refname} — variable name to store result</li>
 *   <li>{@code XPathExtractor.default} — default value if not found</li>
 *   <li>{@code XPathExtractor.matchNumber} — which match to use (1-based)</li>
 *   <li>{@code XPathExtractor.namespace} — enable namespace processing</li>
 * </ul>
 *
 * <p>Uses {@code javax.xml.xpath.XPathFactory} (built into JDK).
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class XPathExtractorExecutor {

    private static final Logger LOG = Logger.getLogger(XPathExtractorExecutor.class.getName());

    private XPathExtractorExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Extracts a value via XPath from the response body and stores it in the variable map.
     *
     * @param node      the XPathExtractor node; must not be {@code null}
     * @param result    the most recent sample result; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String xpathQuery  = VariableResolver.resolve(
                node.getStringProp("XPathExtractor.xpathQuery", ""), variables);
        String refName     = node.getStringProp("XPathExtractor.refname", "");
        String defaultVal  = node.getStringProp("XPathExtractor.default", "");
        String matchNumStr = node.getStringProp("XPathExtractor.matchNumber", "1");
        boolean namespace  = node.getBoolProp("XPathExtractor.namespace");

        if (refName.isBlank()) {
            LOG.log(Level.WARNING, "XPathExtractor [{0}]: refname is empty — skipping",
                    node.getTestName());
            return;
        }

        if (xpathQuery.isBlank()) {
            variables.put(refName, defaultVal);
            return;
        }

        int matchNumber;
        try {
            matchNumber = Integer.parseInt(matchNumStr.trim());
        } catch (NumberFormatException e) {
            matchNumber = 1;
        }

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            variables.put(refName, defaultVal);
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(namespace);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(body)));

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            // Try string evaluation first
            NodeList nodes = (NodeList) xpath.evaluate(xpathQuery, doc, XPathConstants.NODESET);

            if (nodes == null || nodes.getLength() == 0) {
                variables.put(refName, defaultVal);
                // Store match count
                variables.put(refName + "_matchNr", "0");
                return;
            }

            // Store match count
            variables.put(refName + "_matchNr", String.valueOf(nodes.getLength()));

            // Select the target match
            int targetIdx;
            if (matchNumber == 0) {
                // Random match
                targetIdx = (int) (Math.random() * nodes.getLength());
            } else if (matchNumber == -1) {
                // All matches: store each as refName_1, refName_2, etc.
                for (int i = 0; i < nodes.getLength(); i++) {
                    String value = getNodeText(nodes.item(i));
                    variables.put(refName + "_" + (i + 1), value);
                }
                // Also set the primary variable to the first match
                variables.put(refName, getNodeText(nodes.item(0)));
                return;
            } else {
                targetIdx = Math.min(matchNumber - 1, nodes.getLength() - 1);
            }

            String extracted = getNodeText(nodes.item(targetIdx));
            variables.put(refName, extracted);

            LOG.log(Level.FINE, "XPathExtractor [{0}]: {1} = {2}",
                    new Object[]{node.getTestName(), refName, extracted});

        } catch (Exception e) {
            variables.put(refName, defaultVal);
            LOG.log(Level.WARNING, "XPathExtractor [" + node.getTestName() + "]: error — " + e.getMessage(), e);
        }
    }

    private static String getNodeText(org.w3c.dom.Node node) {
        if (node == null) return "";
        String text = node.getTextContent();
        return text != null ? text.trim() : "";
    }
}
