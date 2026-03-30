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
