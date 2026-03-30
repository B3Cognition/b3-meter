/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service.plan;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * StAX-based parser that converts a JMX file into a {@link PlanNode} tree.
 *
 * <h2>JMX structure</h2>
 * JMX files use an alternating element / {@code <hashTree>} pattern:
 * <pre>{@code
 * <jmeterTestPlan version="1.2" ...>
 *   <hashTree>
 *     <TestPlan testclass="TestPlan" testname="My Plan">
 *       <stringProp name="TestPlan.comments">A test</stringProp>
 *     </TestPlan>
 *     <hashTree>
 *       <ThreadGroup testclass="ThreadGroup" testname="Users">
 *         <intProp name="ThreadGroup.num_threads">10</intProp>
 *       </ThreadGroup>
 *       <hashTree>
 *         <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="GET">...</HTTPSamplerProxy>
 *         <hashTree/>
 *       </hashTree>
 *     </hashTree>
 *   </hashTree>
 * </jmeterTestPlan>
 * }</pre>
 *
 * <p>The invariant is: inside every {@code <hashTree>}, elements appear in pairs:
 * <pre>
 *   element-1  &lt;hashTree&gt;...&lt;/hashTree&gt;
 *   element-2  &lt;hashTree&gt;...&lt;/hashTree&gt;
 * </pre>
 * Every element is always followed by exactly one {@code <hashTree>} block (which
 * may be empty: {@code <hashTree/>}).  That hashTree contains the element's children.
 *
 * <h2>Supported property elements</h2>
 * <ul>
 *   <li>{@code <stringProp name="…">value</stringProp>} → {@link String}</li>
 *   <li>{@code <intProp name="…">value</intProp>} → {@link Integer}</li>
 *   <li>{@code <longProp name="…">value</longProp>} → {@link Long}</li>
 *   <li>{@code <boolProp name="…">value</boolProp>} → {@link Boolean}</li>
 *   <li>{@code <doubleProp name="…">value</doubleProp>} → {@link Double}</li>
 *   <li>{@code <elementProp name="…" elementType="…">…</elementProp>} → nested {@link PlanNode}</li>
 *   <li>{@code <collectionProp name="…">…</collectionProp>} → {@link List}</li>
 * </ul>
 *
 * <p>Unknown element types are silently skipped; malformed XML surfaces as a
 * checked {@link JmxParseException}.
 *
 * <p>This class has zero dependency on old jMeter classes — it uses only
 * {@code javax.xml.stream} (StAX), which is part of the Java SE platform.
 */
public final class JmxTreeWalker {

    // Property element names recognised by the parser
    private static final String STRING_PROP      = "stringProp";
    private static final String INT_PROP         = "intProp";
    private static final String LONG_PROP        = "longProp";
    private static final String BOOL_PROP        = "boolProp";
    private static final String DOUBLE_PROP      = "doubleProp";
    private static final String ELEMENT_PROP     = "elementProp";
    private static final String COLLECTION_PROP  = "collectionProp";
    private static final String HASH_TREE        = "hashTree";
    private static final String JMETER_TEST_PLAN = "jmeterTestPlan";

    private JmxTreeWalker() {
        // utility class — not instantiable
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Parses the JMX XML from {@code jmxStream} and returns the root
     * {@link PlanNode} (corresponding to the {@code <jmeterTestPlan>} element).
     *
     * @param jmxStream source XML stream; must not be {@code null}
     * @return root node; never {@code null}
     * @throws JmxParseException    if the XML is malformed or structurally invalid
     * @throws NullPointerException if {@code jmxStream} is {@code null}
     */
    public static PlanNode parse(InputStream jmxStream) throws JmxParseException {
        Objects.requireNonNull(jmxStream, "jmxStream must not be null");
        XMLInputFactory factory = createSecureFactory();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(jmxStream, "UTF-8");
            try {
                return doParse(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new JmxParseException("Failed to parse JMX XML: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the JMX XML from the given string and returns the root
     * {@link PlanNode}.
     *
     * @param jmxXml JMX XML content; must not be {@code null}
     * @return root node; never {@code null}
     * @throws JmxParseException    if the XML is malformed or structurally invalid
     * @throws NullPointerException if {@code jmxXml} is {@code null}
     */
    public static PlanNode parse(String jmxXml) throws JmxParseException {
        Objects.requireNonNull(jmxXml, "jmxXml must not be null");
        byte[] bytes = jmxXml.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            return parse(is);
        } catch (IOException e) {
            // ByteArrayInputStream.close() never throws — defensive only
            throw new JmxParseException("Unexpected I/O error reading JMX string", e);
        }
    }

    // =========================================================================
    // Parser implementation
    // =========================================================================

    /**
     * Creates an {@link XMLInputFactory} with external entity / DTD loading
     * disabled to prevent XXE attacks.
     */
    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return factory;
    }

    /**
     * Locates the {@code <jmeterTestPlan>} root element and returns a parsed
     * {@link PlanNode} for the full plan tree.
     */
    private static PlanNode doParse(XMLStreamReader reader)
            throws XMLStreamException, JmxParseException {

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if (JMETER_TEST_PLAN.equals(localName)) {
                    String version = attr(reader, "version", "1.2");
                    PlanNode.Builder rootBuilder = PlanNode.builder(JMETER_TEST_PLAN, "jmeterTestPlan")
                            .property("version", version);
                    parseJmeterTestPlanBody(reader, rootBuilder);
                    return rootBuilder.build();
                }
                // Skip any unexpected leading elements
                skipElement(reader, localName);
            }
        }
        throw new JmxParseException("No <jmeterTestPlan> root element found in JMX document");
    }

    /**
     * Parses the body of the {@code <jmeterTestPlan>} element.
     *
     * <p>The body is expected to contain exactly one top-level {@code <hashTree>}
     * whose contents become the root's children.
     */
    private static void parseJmeterTestPlanBody(XMLStreamReader reader,
                                                 PlanNode.Builder rootBuilder)
            throws XMLStreamException, JmxParseException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if (HASH_TREE.equals(name)) {
                    for (PlanNode child : parseHashTreeChildren(reader)) {
                        rootBuilder.child(child);
                    }
                } else {
                    skipElement(reader, name);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return; // </jmeterTestPlan>
            }
        }
    }

    /**
     * Parses the contents of a {@code <hashTree>} block and returns the
     * ordered list of child {@link PlanNode}s.
     *
     * <p>The cursor must be positioned immediately after the opening
     * {@code <hashTree>} tag (i.e. after calling {@code reader.next()} returned
     * a START_ELEMENT for "hashTree"). The method reads events until it
     * encounters the matching {@code </hashTree>} end tag.
     *
     * <h3>Algorithm</h3>
     * We maintain a {@code result} list. When we see:
     * <ul>
     *   <li>A principal element start tag (not hashTree, not a property): we parse
     *       its body (properties only) and append a new node to {@code result}.</li>
     *   <li>A {@code <hashTree>} start tag: we recursively parse its children and
     *       attach them to the LAST node in {@code result} (which is the owner).</li>
     *   <li>A {@code </hashTree>} end tag: we return {@code result}.</li>
     * </ul>
     *
     * @param reader StAX reader positioned after {@code <hashTree>} start
     * @return ordered list of parsed child nodes; never {@code null}
     */
    private static List<PlanNode> parseHashTreeChildren(XMLStreamReader reader)
            throws XMLStreamException, JmxParseException {
        List<PlanNode> result = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if (HASH_TREE.equals(localName)) {
                    // This hashTree contains the children of the last element we built.
                    List<PlanNode> children = parseHashTreeChildren(reader);
                    if (!result.isEmpty() && !children.isEmpty()) {
                        // Rebuild the last node with its children attached.
                        int lastIdx = result.size() - 1;
                        PlanNode owner = result.get(lastIdx);
                        result.set(lastIdx, rebuildWithChildren(owner, children).build());
                    }
                    // If result is empty (orphan hashTree), discard children gracefully.

                } else if (isPropElement(localName)) {
                    // Property elements at hashTree top level are unexpected in valid JMX.
                    // Skip them to be robust against slightly malformed plans.
                    skipElement(reader, localName);

                } else {
                    // Principal element (TestPlan, ThreadGroup, HTTPSamplerProxy, …):
                    // parse its inline property body, then add to result.
                    // The following <hashTree> block (if any) will attach children.
                    PlanNode.Builder b = buildPrincipal(reader, localName);
                    parsePrincipalBody(reader, localName, b);
                    result.add(b.build());
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                // </hashTree> — this block is done.
                return result;
            }
            // CHARACTERS, COMMENT, PI, whitespace — silently ignored
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Property parsing
    // -------------------------------------------------------------------------

    private static boolean isPropElement(String name) {
        return STRING_PROP.equals(name)
                || INT_PROP.equals(name)
                || LONG_PROP.equals(name)
                || BOOL_PROP.equals(name)
                || DOUBLE_PROP.equals(name)
                || ELEMENT_PROP.equals(name)
                || COLLECTION_PROP.equals(name);
    }

    /**
     * Parses a single property element (cursor is on its START_ELEMENT) and
     * adds the typed value to {@code builder}.  Always consumes the matching
     * END_ELEMENT before returning.
     */
    private static void parseProperty(XMLStreamReader reader, String elemName,
                                       PlanNode.Builder builder)
            throws XMLStreamException, JmxParseException {
        String propName = attr(reader, "name", null);
        if (propName == null) {
            // Property with no name attribute — skip gracefully
            skipElement(reader, elemName);
            return;
        }

        switch (elemName) {
            case STRING_PROP:
                builder.property(propName, readText(reader));
                break;
            case INT_PROP:
                builder.property(propName, parseIntSafe(readText(reader)));
                break;
            case LONG_PROP:
                builder.property(propName, parseLongSafe(readText(reader)));
                break;
            case BOOL_PROP:
                builder.property(propName, parseBoolSafe(readText(reader)));
                break;
            case DOUBLE_PROP:
                builder.property(propName, parseDoubleSafe(readText(reader)));
                break;
            case ELEMENT_PROP:
                builder.property(propName, parseElementProp(reader, propName));
                break;
            case COLLECTION_PROP:
                builder.property(propName, parseCollectionProp(reader));
                break;
            default:
                skipElement(reader, elemName);
        }
    }

    /**
     * Parses an {@code <elementProp>} into a nested {@link PlanNode}.
     * Cursor is on the START_ELEMENT of {@code elementProp}.
     */
    private static PlanNode parseElementProp(XMLStreamReader reader, String propName)
            throws XMLStreamException, JmxParseException {
        String elementType = attr(reader, "elementType", propName);
        PlanNode.Builder b = PlanNode.builder(elementType, propName);
        parsePrincipalBody(reader, ELEMENT_PROP, b);
        return b.build();
    }

    /**
     * Parses a {@code <collectionProp>} into a {@link List}.
     * Cursor is on the START_ELEMENT of {@code collectionProp}.
     *
     * <p>Uses a manual depth counter because items may themselves be nested
     * elementProp blocks. The counter starts at 1 for the opening
     * {@code <collectionProp>} tag and decrements to 0 when the matching
     * {@code </collectionProp>} is reached.
     */
    private static List<Object> parseCollectionProp(XMLStreamReader reader)
            throws XMLStreamException, JmxParseException {
        List<Object> items = new ArrayList<>();
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                depth++;
                if (ELEMENT_PROP.equals(name)) {
                    String epName = attr(reader, "name", "");
                    String epType = attr(reader, "elementType", epName);
                    PlanNode.Builder b = PlanNode.builder(epType, epName);
                    parsePrincipalBody(reader, ELEMENT_PROP, b);
                    items.add(b.build());
                    depth--; // parsePrincipalBody consumed the </elementProp> end tag
                } else if (isPropElement(name)) {
                    String pName = attr(reader, "name", "");
                    PlanNode.Builder tmp = PlanNode.builder("_prop", pName);
                    parseProperty(reader, name, tmp);
                    // Extract the typed value and add to items list
                    Object val = tmp.build().getProperties().get(pName);
                    if (val != null) items.add(val);
                    depth--; // parseProperty consumed the end tag
                }
                // Unrecognised child elements have depth++ but no depth--,
                // so they will be drained by subsequent END_ELEMENT events.
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return items;
    }

    // -------------------------------------------------------------------------
    // Principal element parsing
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link PlanNode.Builder} from the attributes on the current
     * START_ELEMENT event: {@code testclass}, {@code testname}, {@code guiclass}.
     */
    private static PlanNode.Builder buildPrincipal(XMLStreamReader reader, String localName) {
        String testClass = attr(reader, "testclass", localName);
        String testName  = attr(reader, "testname",  localName);
        PlanNode.Builder b = PlanNode.builder(testClass, testName);
        String guiClass = attr(reader, "guiclass", null);
        if (guiClass != null) {
            b.property("guiclass", guiClass);
        }
        return b;
    }

    /**
     * Parses the body of a principal element: reads property child elements
     * ({@code stringProp}, {@code intProp}, {@code elementProp}, etc.) until the
     * matching closing tag for {@code elementLocalName}.
     *
     * <p>This method handles the ELEMENT's own body only — it does NOT process
     * nested {@code <hashTree>} blocks (those belong to the outer parse loop).
     * In valid JMX, hashTree blocks never appear inside a principal element's body.
     *
     * <p>Depth tracking: {@code depth=1} represents the outer element. Each child
     * START_ELEMENT that is NOT handled by a recursive call would increment depth,
     * but since {@link #parseProperty} and {@link #skipElement} always consume
     * their own END_ELEMENT, depth stays at 1 until the outer element's own
     * END_ELEMENT is reached (depth → 0).
     *
     * @param reader            positioned after the principal element's START_ELEMENT
     * @param elementLocalName  local name of the element whose body we are parsing
     * @param builder           builder to receive parsed properties
     */
    private static void parsePrincipalBody(XMLStreamReader reader, String elementLocalName,
                                            PlanNode.Builder builder)
            throws XMLStreamException, JmxParseException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if (isPropElement(name)) {
                    parseProperty(reader, name, builder);
                    // parseProperty always consumes its own END_ELEMENT — depth unchanged
                } else {
                    skipElement(reader, name);
                    // skipElement consumes through the matching END_ELEMENT — depth unchanged
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Low-level helpers
    // -------------------------------------------------------------------------

    /**
     * Reads all text / CDATA content until the next END_ELEMENT event, which is
     * also consumed.  Returns the accumulated text.
     */
    private static String readText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS
                    || event == XMLStreamConstants.CDATA) {
                sb.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return sb.toString();
            }
        }
        return sb.toString();
    }

    /**
     * Skips an element by consuming all events until (and including) the
     * matching END_ELEMENT.  The caller must have already consumed the element's
     * START_ELEMENT.
     */
    private static void skipElement(XMLStreamReader reader, String localName)
            throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    /** Returns the value of XML attribute {@code name}, or {@code defaultValue} if absent. */
    private static String attr(XMLStreamReader reader, String name, String defaultValue) {
        String v = reader.getAttributeValue(null, name);
        return (v != null) ? v : defaultValue;
    }

    /**
     * Rebuilds a {@link PlanNode} preserving all its existing properties and
     * prepending any existing children, then appending {@code newChildren}.
     */
    private static PlanNode.Builder rebuildWithChildren(PlanNode node, List<PlanNode> newChildren) {
        PlanNode.Builder b = PlanNode.builder(node.getTestClass(), node.getTestName());
        node.getProperties().forEach(b::property);
        // Preserve any children the node already had (none in normal JMX, but be safe)
        for (PlanNode existing : node.getChildren()) {
            b.child(existing);
        }
        for (PlanNode child : newChildren) {
            b.child(child);
        }
        return b;
    }

    // =========================================================================
    // Type converters
    // =========================================================================

    private static int parseIntSafe(String text) {
        if (text == null || text.isBlank()) return 0;
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long parseLongSafe(String text) {
        if (text == null || text.isBlank()) return 0L;
        try { return Long.parseLong(text.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static boolean parseBoolSafe(String text) {
        if (text == null) return false;
        return "true".equalsIgnoreCase(text.trim());
    }

    private static double parseDoubleSafe(String text) {
        if (text == null || text.isBlank()) return 0.0;
        try { return Double.parseDouble(text.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
