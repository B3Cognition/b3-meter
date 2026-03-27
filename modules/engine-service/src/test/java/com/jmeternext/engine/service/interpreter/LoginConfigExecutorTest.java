package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
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
