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
package com.b3meter.engine.adapter.security;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;

/**
 * Configures XStream with an explicit type allowlist instead of AnyTypePermission.ANY.
 *
 * <p>Addresses CVE-2013-7285 and all subsequent XStream deserialization CVEs.
 * The old jMeter code used {@code xstream.addPermission(AnyTypePermission.ANY)},
 * which is effectively no security at all — any class on the JVM classpath can be
 * instantiated by a crafted XML payload.
 *
 * <p>This policy applies a deny-all baseline and then explicitly allows only the
 * types that are required for JMX file deserialization. Gadget chain classes
 * (Commons Collections, Groovy MethodClosure, Xalan TemplatesImpl) are blocked
 * by the deny-all baseline.
 *
 * <p>Implements FR-SEC-001.
 */
public final class XStreamSecurityPolicy {

    private XStreamSecurityPolicy() {
        // utility class — not instantiable
    }

    /**
     * Apply the secure allowlist to an XStream instance.
     *
     * <p>Only b3meter's own types, selected standard Java types, and
     * commons-lang3 are permitted. All other classes are blocked.
     *
     * @param xstream the XStream instance to configure; must not be {@code null}
     */
    public static void apply(XStream xstream) {
        if (xstream == null) {
            throw new IllegalArgumentException("xstream must not be null");
        }

        // Deny everything first — this is the secure baseline.
        xstream.addPermission(NoTypePermission.NONE);

        // Allow b3meter's own types (required for JMX deserialization).
        xstream.allowTypesByWildcard(new String[]{
            "com.b3meter.**"
        });

        // Allow standard Java collection types needed by JMX format.
        // Single-star (java.util.*) — direct package only, NOT sub-packages.
        // java.util.concurrent.** is NOT allowlisted — not required by JMX format
        // and includes classes that can participate in deserialization gadget chains.
        xstream.allowTypesByWildcard(new String[]{
            "java.util.*",
            "java.util.regex.*"
        });
        xstream.allowTypesByWildcard(new String[]{
            "java.lang.String",
            "java.lang.Boolean",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Character",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Number",
            "java.io.File",
            "java.net.URL",
            "java.net.URI"
        });

        // Allow commons-lang3 (used by some JMeter properties).
        xstream.allowTypesByWildcard(new String[]{
            "org.apache.commons.lang3.**"
        });

        // NOTE: java.lang.Runtime, java.lang.ProcessBuilder, and all known
        // gadget chain classes (Commons Collections InvokerTransformer,
        // Groovy MethodClosure, Xalan TemplatesImpl) are denied by
        // NoTypePermission.NONE above. No additional deny calls are needed.
    }

    /**
     * Register a plugin's types for XStream deserialization.
     *
     * <p>Plugins that define custom JMX elements must explicitly register their
     * package wildcards here before any deserialization takes place. This is the
     * sanctioned extension point — do not use {@code AnyTypePermission.ANY} in
     * plugin code.
     *
     * @param xstream         the XStream instance to configure; must not be {@code null}
     * @param packageWildcards wildcard patterns accepted by XStream's
     *                         {@code allowTypesByWildcard}, e.g. {@code "com.example.plugin.**"}
     */
    private static final java.util.Set<String> DENY_LIST_PREFIXES = java.util.Set.of(
        "**", "java.lang.Runtime", "java.lang.ProcessBuilder",
        "java.lang.reflect.", "org.codehaus.groovy.",
        "org.springframework.", "org.apache.commons.collections.",
        "org.apache.commons.collections4.", "org.apache.commons.beanutils.",
        "sun.", "com.sun.", "com.thoughtworks.xstream."
    );

    public static void registerPluginTypes(XStream xstream, String... packageWildcards) {
        if (xstream == null) {
            throw new IllegalArgumentException("xstream must not be null");
        }
        if (packageWildcards == null || packageWildcards.length == 0) {
            return;
        }
        for (String pattern : packageWildcards) {
            for (String blocked : DENY_LIST_PREFIXES) {
                if (pattern.equals(blocked) || pattern.startsWith(blocked)) {
                    throw new SecurityException(
                        "Rejected plugin type wildcard '" + pattern +
                        "': matches a known gadget chain package. See XStreamSecurityPolicy javadoc.");
                }
            }
        }
        xstream.allowTypesByWildcard(packageWildcards);
    }
}
