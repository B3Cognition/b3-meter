package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes a {@code KeystoreConfig} {@link PlanNode} to store SSL/TLS keystore
 * configuration in the VU variable map.
 *
 * <p>This is a pure config element: it reads keystore properties and stores them
 * as variables for the HTTP sampler to use when establishing TLS connections.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code preload} — preload the keystore at test start</li>
 *   <li>{@code startIndex} — start index for client cert selection</li>
 *   <li>{@code endIndex} — end index for client cert selection</li>
 *   <li>{@code clientCertAliasVarName} — variable name containing the cert alias</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class KeystoreConfigExecutor {

    private static final Logger LOG = Logger.getLogger(KeystoreConfigExecutor.class.getName());

    /** Variable key prefix for keystore configuration. */
    private static final String KS_PREFIX = "__jmn_keystore_";

    private KeystoreConfigExecutor() {}

    /**
     * Reads keystore properties from the plan node and stores them in
     * the VU variable map.
     *
     * @param node      the KeystoreConfig node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        boolean preload = node.getBoolProp("preload", true);
        String startIndex = node.getStringProp("startIndex", "0");
        String endIndex = node.getStringProp("endIndex", "");
        String clientCertAliasVarName = node.getStringProp("clientCertAliasVarName", "");

        variables.put(KS_PREFIX + "configured", "true");
        variables.put(KS_PREFIX + "preload", String.valueOf(preload));
        variables.put(KS_PREFIX + "startIndex", startIndex);
        variables.put(KS_PREFIX + "endIndex", endIndex);
        variables.put(KS_PREFIX + "clientCertAliasVarName", clientCertAliasVarName);

        LOG.log(Level.FINE,
                "KeystoreConfigExecutor [{0}]: configured (preload={1}, startIndex={2}, endIndex={3})",
                new Object[]{node.getTestName(), preload, startIndex, endIndex});
    }
}
