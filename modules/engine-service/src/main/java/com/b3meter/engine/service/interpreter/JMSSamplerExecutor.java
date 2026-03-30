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
 * Executes {@code PublisherSampler} and {@code SubscriberSampler} {@link PlanNode}s.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code jms.initial_context_factory} — JNDI initial context factory class</li>
 *   <li>{@code jms.provider_url} — JNDI provider URL</li>
 *   <li>{@code jms.topic} — JMS destination (topic or queue)</li>
 *   <li>{@code jms.security_principal} — JNDI security principal</li>
 *   <li>{@code jms.security_credentials} — JNDI security credentials</li>
 *   <li>{@code jms.text_message} — message body (publisher only)</li>
 *   <li>{@code jms.connection_factory} — JNDI name for connection factory</li>
 *   <li>{@code jms.timeout} — receive timeout in ms (subscriber only)</li>
 * </ul>
 *
 * <p>This is a STUB implementation. JMS requires a JMS provider (ActiveMQ, RabbitMQ, etc.)
 * on the classpath. The stub parses all JMX properties so imported test plans do not crash,
 * and returns a 501 (Not Implemented) result.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JMSSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(JMSSamplerExecutor.class.getName());

    private JMSSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes a JMS Publisher sampler.
     *
     * @param node      the PublisherSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void executePublisher(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String contextFactory = resolve(node.getStringProp("jms.initial_context_factory", ""), variables);
        String providerUrl = resolve(node.getStringProp("jms.provider_url", ""), variables);
        String topic = resolve(node.getStringProp("jms.topic", ""), variables);
        String principal = resolve(node.getStringProp("jms.security_principal", ""), variables);
        String credentials = resolve(node.getStringProp("jms.security_credentials", ""), variables);
        String textMessage = resolve(node.getStringProp("jms.text_message", ""), variables);
        String connectionFactory = resolve(node.getStringProp("jms.connection_factory", ""), variables);

        LOG.log(Level.WARNING,
                "JMSSamplerExecutor: JMS Publisher not available -- requires JMS provider on classpath. "
                + "Topic={0}, Provider={1}",
                new Object[]{topic, providerUrl});

        result.setStatusCode(501);
        result.setResponseBody(
                "JMS Publisher sampler is a stub. JMS requires a provider (e.g. ActiveMQ) on the classpath.\n"
                + "Context Factory: " + contextFactory + "\n"
                + "Provider URL: " + providerUrl + "\n"
                + "Topic: " + topic + "\n"
                + "Connection Factory: " + connectionFactory + "\n"
                + "Message length: " + textMessage.length() + " chars");
        result.setFailureMessage("JMS not available -- requires JMS provider on classpath");
    }

    /**
     * Executes a JMS Subscriber sampler.
     *
     * @param node      the SubscriberSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void executeSubscriber(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String contextFactory = resolve(node.getStringProp("jms.initial_context_factory", ""), variables);
        String providerUrl = resolve(node.getStringProp("jms.provider_url", ""), variables);
        String topic = resolve(node.getStringProp("jms.topic", ""), variables);
        String principal = resolve(node.getStringProp("jms.security_principal", ""), variables);
        String credentials = resolve(node.getStringProp("jms.security_credentials", ""), variables);
        String connectionFactory = resolve(node.getStringProp("jms.connection_factory", ""), variables);
        int timeout = node.getIntProp("jms.timeout", 5000);

        LOG.log(Level.WARNING,
                "JMSSamplerExecutor: JMS Subscriber not available -- requires JMS provider on classpath. "
                + "Topic={0}, Provider={1}",
                new Object[]{topic, providerUrl});

        result.setStatusCode(501);
        result.setResponseBody(
                "JMS Subscriber sampler is a stub. JMS requires a provider (e.g. ActiveMQ) on the classpath.\n"
                + "Context Factory: " + contextFactory + "\n"
                + "Provider URL: " + providerUrl + "\n"
                + "Topic: " + topic + "\n"
                + "Connection Factory: " + connectionFactory + "\n"
                + "Timeout: " + timeout + "ms");
        result.setFailureMessage("JMS not available -- requires JMS provider on classpath");
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
