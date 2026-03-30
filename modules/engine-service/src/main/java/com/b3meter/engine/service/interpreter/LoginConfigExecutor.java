package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes a {@code LoginConfig} {@link PlanNode} to store HTTP authentication
 * credentials in the VU variable map.
 *
 * <p>This is a pure config element: it reads username and password from the
 * plan node and stores them as variables for HTTP samplers to use.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code ConfigTestElement.username} — the login username</li>
 *   <li>{@code ConfigTestElement.password} — the login password</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class LoginConfigExecutor {

    private static final Logger LOG = Logger.getLogger(LoginConfigExecutor.class.getName());

    private LoginConfigExecutor() {}

    /**
     * Reads login credentials from the plan node and stores them in
     * the VU variable map.
     *
     * @param node      the LoginConfig node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String username = node.getStringProp("ConfigTestElement.username", "");
        String password = node.getStringProp("ConfigTestElement.password", "");

        variables.put("__jmn_login_username", username);
        variables.put("__jmn_login_password", password);
        variables.put("__jmn_login_configured", "true");

        LOG.log(Level.FINE,
                "LoginConfigExecutor [{0}]: configured (username={1})",
                new Object[]{node.getTestName(), username});
    }
}
