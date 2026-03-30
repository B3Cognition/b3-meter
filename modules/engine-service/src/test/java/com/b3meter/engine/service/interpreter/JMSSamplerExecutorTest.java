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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JMSSamplerExecutor}.
 */
class JMSSamplerExecutorTest {

    // =========================================================================
    // Null guards — Publisher
    // =========================================================================

    @Test
    void publisherThrowsOnNullNode() {
        SampleResult r = new SampleResult("jms-pub");
        assertThrows(NullPointerException.class,
                () -> JMSSamplerExecutor.executePublisher(null, r, Map.of()));
    }

    @Test
    void publisherThrowsOnNullResult() {
        PlanNode node = pubNode("tcp://localhost:61616", "test.topic");
        assertThrows(NullPointerException.class,
                () -> JMSSamplerExecutor.executePublisher(node, null, Map.of()));
    }

    @Test
    void publisherThrowsOnNullVariables() {
        PlanNode node = pubNode("tcp://localhost:61616", "test.topic");
        SampleResult r = new SampleResult("jms-pub");
        assertThrows(NullPointerException.class,
                () -> JMSSamplerExecutor.executePublisher(node, r, null));
    }

    // =========================================================================
    // Publisher stub behavior
    // =========================================================================

    @Test
    void publisherReturns501Stub() {
        PlanNode node = PlanNode.builder("PublisherSampler", "jms-pub-test")
                .property("jms.initial_context_factory", "org.apache.activemq.jndi.ActiveMQInitialContextFactory")
                .property("jms.provider_url", "tcp://broker:61616")
                .property("jms.topic", "test.queue")
                .property("jms.text_message", "Hello JMS")
                .property("jms.connection_factory", "ConnectionFactory")
                .build();

        SampleResult result = new SampleResult("jms-pub-test");
        JMSSamplerExecutor.executePublisher(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(501, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("stub"));
        assertTrue(result.getResponseBody().contains("test.queue"));
        assertTrue(result.getResponseBody().contains("tcp://broker:61616"));
        assertTrue(result.getFailureMessage().contains("JMS not available"));
    }

    @Test
    void publisherResolvesVariables() {
        PlanNode node = PlanNode.builder("PublisherSampler", "jms-pub-vars")
                .property("jms.provider_url", "${broker_url}")
                .property("jms.topic", "${topic}")
                .build();

        Map<String, String> vars = new HashMap<>();
        vars.put("broker_url", "tcp://resolved:61616");
        vars.put("topic", "resolved.topic");

        SampleResult result = new SampleResult("jms-pub-vars");
        JMSSamplerExecutor.executePublisher(node, result, vars);

        assertTrue(result.getResponseBody().contains("resolved.topic"));
        assertTrue(result.getResponseBody().contains("tcp://resolved:61616"));
    }

    // =========================================================================
    // Null guards — Subscriber
    // =========================================================================

    @Test
    void subscriberThrowsOnNullNode() {
        SampleResult r = new SampleResult("jms-sub");
        assertThrows(NullPointerException.class,
                () -> JMSSamplerExecutor.executeSubscriber(null, r, Map.of()));
    }

    // =========================================================================
    // Subscriber stub behavior
    // =========================================================================

    @Test
    void subscriberReturns501Stub() {
        PlanNode node = PlanNode.builder("SubscriberSampler", "jms-sub-test")
                .property("jms.provider_url", "tcp://broker:61616")
                .property("jms.topic", "sub.topic")
                .property("jms.timeout", "10000")
                .build();

        SampleResult result = new SampleResult("jms-sub-test");
        JMSSamplerExecutor.executeSubscriber(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(501, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("Subscriber"));
        assertTrue(result.getResponseBody().contains("sub.topic"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode pubNode(String providerUrl, String topic) {
        return PlanNode.builder("PublisherSampler", "jms-pub")
                .property("jms.provider_url", providerUrl)
                .property("jms.topic", topic)
                .build();
    }
}
