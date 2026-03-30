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
 * Tests for {@link LoginConfigExecutor}.
 */
class LoginConfigExecutorTest {

    @Test
    void configure_setsUsernameAndPassword() {
        PlanNode node = PlanNode.builder("LoginConfig", "Login Config")
                .property("ConfigTestElement.username", "admin")
                .property("ConfigTestElement.password", "secret123")
                .build();

        Map<String, String> variables = new HashMap<>();
        LoginConfigExecutor.configure(node, variables);

        assertEquals("admin", variables.get("__jmn_login_username"));
        assertEquals("secret123", variables.get("__jmn_login_password"));
        assertEquals("true", variables.get("__jmn_login_configured"));
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("LoginConfig", "Login").build();

        Map<String, String> variables = new HashMap<>();
        LoginConfigExecutor.configure(node, variables);

        assertEquals("", variables.get("__jmn_login_username"));
        assertEquals("", variables.get("__jmn_login_password"));
        assertEquals("true", variables.get("__jmn_login_configured"));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> LoginConfigExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("LoginConfig", "test").build();
        assertThrows(NullPointerException.class,
                () -> LoginConfigExecutor.configure(node, null));
    }
}
