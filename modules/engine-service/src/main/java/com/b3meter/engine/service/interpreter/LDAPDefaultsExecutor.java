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
 * Processes an {@code LDAPSamplerBase} (LDAP Request Defaults) {@link PlanNode}
 * to store default LDAP connection properties in the VU variable map.
 *
 * <p>This is a pure config element: it reads LDAP defaults and stores them
 * as variables for LDAP samplers to use when no per-sampler override is set.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code servername} — LDAP server hostname</li>
 *   <li>{@code port} — LDAP server port (default 389)</li>
 *   <li>{@code rootdn} — root distinguished name</li>
 *   <li>{@code test} — test type (e.g. add, delete, search)</li>
 *   <li>{@code base_entry_dn} — base entry DN for operations</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class LDAPDefaultsExecutor {

    private static final Logger LOG = Logger.getLogger(LDAPDefaultsExecutor.class.getName());

    /** Variable key prefix for LDAP defaults. */
    private static final String LDAP_PREFIX = "__jmn_ldap_defaults_";

    private LDAPDefaultsExecutor() {}

    /**
     * Reads LDAP default properties from the plan node and stores them in
     * the VU variable map.
     *
     * @param node      the LDAPSamplerBase (LDAP Request Defaults) node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String servername = node.getStringProp("servername", "");
        String port = node.getStringProp("port", "389");
        String rootdn = node.getStringProp("rootdn", "");
        String test = node.getStringProp("test", "");
        String baseEntryDn = node.getStringProp("base_entry_dn", "");

        variables.put(LDAP_PREFIX + "configured", "true");
        variables.put(LDAP_PREFIX + "servername", servername);
        variables.put(LDAP_PREFIX + "port", port);
        variables.put(LDAP_PREFIX + "rootdn", rootdn);
        variables.put(LDAP_PREFIX + "test", test);
        variables.put(LDAP_PREFIX + "base_entry_dn", baseEntryDn);

        LOG.log(Level.FINE,
                "LDAPDefaultsExecutor [{0}]: configured (server={1}:{2}, rootdn={3})",
                new Object[]{node.getTestName(), servername, port, rootdn});
    }
}
