package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KeystoreConfigExecutor}.
 */
class KeystoreConfigExecutorTest {

    @Test
    void configure_setsAllVariables() {
        PlanNode node = PlanNode.builder("KeystoreConfig", "Keystore Config")
                .property("preload", true)
                .property("startIndex", "0")
                .property("endIndex", "10")
                .property("clientCertAliasVarName", "certAlias")
                .build();

        Map<String, String> variables = new HashMap<>();
        KeystoreConfigExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_keystore_configured"));
        assertEquals("true", variables.get("__jmn_keystore_preload"));
        assertEquals("0", variables.get("__jmn_keystore_startIndex"));
        assertEquals("10", variables.get("__jmn_keystore_endIndex"));
        assertEquals("certAlias", variables.get("__jmn_keystore_clientCertAliasVarName"));
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("KeystoreConfig", "Keystore").build();

        Map<String, String> variables = new HashMap<>();
        KeystoreConfigExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_keystore_configured"));
        assertEquals("true", variables.get("__jmn_keystore_preload"));
        assertEquals("0", variables.get("__jmn_keystore_startIndex"));
        assertEquals("", variables.get("__jmn_keystore_endIndex"));
        assertEquals("", variables.get("__jmn_keystore_clientCertAliasVarName"));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> KeystoreConfigExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("KeystoreConfig", "test").build();
        assertThrows(NullPointerException.class,
                () -> KeystoreConfigExecutor.configure(node, null));
    }
}
