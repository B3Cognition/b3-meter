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
 * Tests for {@link LDAPDefaultsExecutor}.
 */
class LDAPDefaultsExecutorTest {

    @Test
    void configure_setsAllProperties() {
        PlanNode node = PlanNode.builder("LDAPSamplerBase", "LDAP Defaults")
                .property("servername", "ldap.example.com")
                .property("port", "636")
                .property("rootdn", "dc=example,dc=com")
                .property("test", "search")
                .property("base_entry_dn", "ou=people,dc=example,dc=com")
                .build();

        Map<String, String> variables = new HashMap<>();
        LDAPDefaultsExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_ldap_defaults_configured"));
        assertEquals("ldap.example.com", variables.get("__jmn_ldap_defaults_servername"));
        assertEquals("636", variables.get("__jmn_ldap_defaults_port"));
        assertEquals("dc=example,dc=com", variables.get("__jmn_ldap_defaults_rootdn"));
        assertEquals("search", variables.get("__jmn_ldap_defaults_test"));
        assertEquals("ou=people,dc=example,dc=com", variables.get("__jmn_ldap_defaults_base_entry_dn"));
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("LDAPSamplerBase", "LDAP Defaults").build();

        Map<String, String> variables = new HashMap<>();
        LDAPDefaultsExecutor.configure(node, variables);

        assertEquals("true", variables.get("__jmn_ldap_defaults_configured"));
        assertEquals("", variables.get("__jmn_ldap_defaults_servername"));
        assertEquals("389", variables.get("__jmn_ldap_defaults_port"));
        assertEquals("", variables.get("__jmn_ldap_defaults_rootdn"));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> LDAPDefaultsExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("LDAPSamplerBase", "test").build();
        assertThrows(NullPointerException.class,
                () -> LDAPDefaultsExecutor.configure(node, null));
    }
}
