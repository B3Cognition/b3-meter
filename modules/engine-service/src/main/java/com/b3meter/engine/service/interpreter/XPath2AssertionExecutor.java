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

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code XPath2Assertion} {@link PlanNode} against a prior {@link SampleResult}.
 *
 * <p>Reads the standard JMeter XPath2Assertion properties:
 * <ul>
 *   <li>{@code XPath.xpath} — XPath 2.0 expression to evaluate</li>
 *   <li>{@code XPath.negate} — if true, invert the assertion result</li>
 *   <li>{@code XPath.namespace} — if true, enable namespace processing</li>
 *   <li>{@code XPath.tolerant} — if true, use tolerant parsing</li>
 * </ul>
 *
 * <p>Delegates to {@link XPathAssertionExecutor}. The JDK's {@code javax.xml.xpath.XPathFactory}
 * handles most XPath 2.0 expressions that are backward-compatible with XPath 1.0.
 * For full XPath 2.0 support, a Saxon engine on the classpath would be required.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class XPath2AssertionExecutor {

    private static final Logger LOG = Logger.getLogger(XPath2AssertionExecutor.class.getName());

    private XPath2AssertionExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Applies the XPath 2.0 assertion described by {@code node} to {@code result}.
     *
     * <p>Delegates to {@link XPathAssertionExecutor} which uses the JDK's XPath
     * implementation. Most XPath 2.0 expressions that are backward-compatible
     * with XPath 1.0 will work. Full XPath 2.0 support requires Saxon on the classpath.
     *
     * @param node      the XPath2Assertion node; must not be {@code null}
     * @param result    the most recent sample result to validate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        LOG.log(Level.FINE,
                "XPath2AssertionExecutor: delegating to XPathAssertionExecutor (JDK XPath 1.0 engine). "
                + "Full XPath 2.0 requires Saxon on classpath.");

        XPathAssertionExecutor.execute(node, result, variables);
    }
}
