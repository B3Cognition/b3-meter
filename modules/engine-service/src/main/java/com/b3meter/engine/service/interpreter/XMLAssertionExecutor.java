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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.InputSource;

/**
 * Executes an {@code XMLAssertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Simply validates that the response body is well-formed XML.
 * Uses {@code javax.xml.parsers.DocumentBuilderFactory} (built into JDK).
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class XMLAssertionExecutor {

    private static final Logger LOG = Logger.getLogger(XMLAssertionExecutor.class.getName());

    private XMLAssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the XML well-formedness assertion to {@code result}.
     *
     * @param node      the XMLAssertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope (unused but kept for consistency)
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node,      "node must not be null");
        Objects.requireNonNull(result,    "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String body = result.getResponseBody();
        if (body == null || body.isBlank()) {
            result.setFailureMessage("XMLAssertion [" + node.getTestName()
                    + "] FAILED: Response body is empty — not valid XML");
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(body)));
            // Parse succeeded — body is well-formed XML
        } catch (Exception e) {
            result.setFailureMessage("XMLAssertion [" + node.getTestName()
                    + "] FAILED: Response is not well-formed XML — " + e.getMessage());
            LOG.log(Level.FINE, "XMLAssertion [{0}]: parse error", new Object[]{node.getTestName()});
        }
    }
}
