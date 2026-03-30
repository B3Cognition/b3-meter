package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DNSCacheManagerExecutor}.
 */
class DNSCacheManagerExecutorTest {

    @Test
    void configure_setsBasicVariables() {
        PlanNode node = PlanNode.builder("DNSCacheManager", "DNS Cache Manager")
                .property("DNSCacheManager.clearEachIteration", true)
                .property("DNSCacheManager.isCustomResolver", true)
                .build();

        Map<String, String> variables = new HashMap<>();
        DNSCacheManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__dns_enabled"));
        assertEquals("true", variables.get("__dns_clearEachIteration"));
        assertEquals("true", variables.get("__dns_isCustomResolver"));
        assertEquals("0", variables.get("__dns_server_count"));
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("DNSCacheManager", "DNS").build();

        Map<String, String> variables = new HashMap<>();
        DNSCacheManagerExecutor.configure(node, variables);

        assertEquals("true", variables.get("__dns_enabled"));
        assertEquals("false", variables.get("__dns_clearEachIteration"));
        assertEquals("false", variables.get("__dns_isCustomResolver"));
    }

    @Test
    void configure_withServers() {
        PlanNode node = PlanNode.builder("DNSCacheManager", "DNS")
                .property("DNSCacheManager.isCustomResolver", true)
                .property("DNSCacheManager.servers", List.of("8.8.8.8", "8.8.4.4"))
                .build();

        Map<String, String> variables = new HashMap<>();
        DNSCacheManagerExecutor.configure(node, variables);

        assertEquals("2", variables.get("__dns_server_count"));
        assertEquals("8.8.8.8", variables.get("__dns_server_0"));
        assertEquals("8.8.4.4", variables.get("__dns_server_1"));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> DNSCacheManagerExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("DNSCacheManager", "test").build();
        assertThrows(NullPointerException.class,
                () -> DNSCacheManagerExecutor.configure(node, null));
    }
}
