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
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.security.ForbiddenClassException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for {@link XStreamSecurityPolicy}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Known-safe types (b3meter, java.util, java.lang primitives) deserialize correctly.</li>
 *   <li>Known gadget chain classes raise {@link ForbiddenClassException}.</li>
 *   <li>Dangerous JVM classes (Runtime, ProcessBuilder) are blocked.</li>
 *   <li>The plugin registration API extends the allowlist correctly.</li>
 * </ul>
 *
 * <p>Tagged {@code @Tag("security")} so CI can run these as a blocking gate.
 */
@Tag("security")
class XStreamSecurityPolicyTest {

    private XStream xstream;

    @BeforeEach
    void setUp() {
        xstream = new XStream();
        XStreamSecurityPolicy.apply(xstream);
    }

    // -------------------------------------------------------------------------
    // apply() — null guard
    // -------------------------------------------------------------------------

    @Test
    void applyThrowsOnNullXstream() {
        assertThrows(IllegalArgumentException.class,
                () -> XStreamSecurityPolicy.apply(null));
    }

    // -------------------------------------------------------------------------
    // allow: b3meter own types
    // -------------------------------------------------------------------------

    @Test
    void allowsB3MeterTypes() {
        // com.b3meter.** is covered by XStreamSecurityPolicy.apply() called in @BeforeEach.
        // Do NOT re-grant allowlist here — that would make this test vacuous as a regression guard.
        // AllowedTestBean lives in com.b3meter.engine.adapter.security, covered by com.b3meter.**.
        AllowedTestBean original = new AllowedTestBean("hello", 42);
        String xml = xstream.toXML(original);

        AllowedTestBean result = (AllowedTestBean) xstream.fromXML(xml);

        assertEquals("hello", result.value);
        assertEquals(42, result.number);
    }

    // -------------------------------------------------------------------------
    // allow: standard Java types
    // -------------------------------------------------------------------------

    @Test
    void allowsJavaUtilHashMap() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        String xml = xstream.toXML(map);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) xstream.fromXML(xml);

        assertEquals("value", result.get("key"));
    }

    @Test
    void allowsJavaUtilArrayList() {
        List<String> list = new ArrayList<>();
        list.add("alpha");
        list.add("beta");
        String xml = xstream.toXML(list);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) xstream.fromXML(xml);

        assertEquals(2, result.size());
        assertEquals("alpha", result.get(0));
    }

    @Test
    void allowsJavaLangString() {
        String xml = xstream.toXML("safe-string");
        String result = (String) xstream.fromXML(xml);
        assertEquals("safe-string", result);
    }

    // -------------------------------------------------------------------------
    // block: Commons Collections gadget chain
    // -------------------------------------------------------------------------

    @Test
    void blocksCommonsCollectionsInvokerTransformer() {
        // Craft minimal XML for a known gadget chain class.
        String xml = "<org.apache.commons.collections4.functors.InvokerTransformer>"
                + "<iMethodName>exec</iMethodName>"
                + "</org.apache.commons.collections4.functors.InvokerTransformer>";

        assertDeserializationBlocked(xml,
                "InvokerTransformer must be blocked by the security policy");
    }

    @Test
    void blocksCommonsCollectionsChainedTransformer() {
        String xml = "<org.apache.commons.collections4.functors.ChainedTransformer>"
                + "<iTransformers/>"
                + "</org.apache.commons.collections4.functors.ChainedTransformer>";

        assertDeserializationBlocked(xml,
                "ChainedTransformer must be blocked by the security policy");
    }

    // -------------------------------------------------------------------------
    // block: Groovy MethodClosure (ships with JMeter)
    // -------------------------------------------------------------------------

    @Test
    void blocksGroovyMethodClosure() {
        String xml = "<org.codehaus.groovy.runtime.MethodClosure>"
                + "<delegate class=\"java.lang.Runtime\"/>"
                + "<method>exec</method>"
                + "</org.codehaus.groovy.runtime.MethodClosure>";

        assertDeserializationBlocked(xml,
                "Groovy MethodClosure must be blocked by the security policy");
    }

    // -------------------------------------------------------------------------
    // block: java.lang.Runtime (OS command execution)
    // -------------------------------------------------------------------------

    @Test
    void blocksRuntimeExec() {
        String xml = "<java.lang.Runtime/>";

        assertThrows(ForbiddenClassException.class,
                () -> xstream.fromXML(xml),
                "java.lang.Runtime must be blocked by the security policy");
    }

    // -------------------------------------------------------------------------
    // block: java.lang.ProcessBuilder
    // -------------------------------------------------------------------------

    @Test
    void blocksProcessBuilder() {
        String xml = "<java.lang.ProcessBuilder>"
                + "<command><string>id</string></command>"
                + "</java.lang.ProcessBuilder>";

        assertThrows(ForbiddenClassException.class,
                () -> xstream.fromXML(xml),
                "java.lang.ProcessBuilder must be blocked by the security policy");
    }

    // -------------------------------------------------------------------------
    // block: Xalan TemplatesImpl (bytecode injection)
    // -------------------------------------------------------------------------

    @Test
    void blocksXalanTemplatesImpl() {
        String xml = "<com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl/>";

        assertThrows(ForbiddenClassException.class,
                () -> xstream.fromXML(xml),
                "TemplatesImpl must be blocked by the security policy");
    }

    // -------------------------------------------------------------------------
    // plugin registration API
    // -------------------------------------------------------------------------

    @Test
    void pluginRegistrationAllowsCustomTypes() {
        // Register an external package via the plugin API and verify it works.
        XStreamSecurityPolicy.registerPluginTypes(xstream,
                "com.example.myplugin.**",
                AllowedTestBean.class.getPackageName() + ".**");

        AllowedTestBean original = new AllowedTestBean("plugin-value", 99);
        String xml = xstream.toXML(original);

        AllowedTestBean result = (AllowedTestBean) xstream.fromXML(xml);
        assertEquals("plugin-value", result.value);
        assertEquals(99, result.number);
    }

    @Test
    void pluginRegistrationWithNullXstreamThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> XStreamSecurityPolicy.registerPluginTypes(null, "com.example.**"));
    }

    @Test
    void pluginRegistrationWithEmptyWildcardsIsNoOp() {
        // Must not throw — an empty registration is a no-op, not an error.
        assertDoesNotThrow(() -> XStreamSecurityPolicy.registerPluginTypes(xstream));
    }

    @Test
    void pluginRegistrationWithNullWildcardsIsNoOp() {
        assertDoesNotThrow(() -> XStreamSecurityPolicy.registerPluginTypes(xstream, (String[]) null));
    }

    // -------------------------------------------------------------------------
    // Helper: assert that deserialization of the given XML is blocked.
    //
    // XStream raises ForbiddenClassException when the class is present on the
    // classpath but the security policy denies it.  When the class is absent
    // from the classpath entirely it raises CannotResolveClassException.
    // Both outcomes prevent the gadget chain from executing — the attack fails
    // in either case.
    // -------------------------------------------------------------------------

    private void assertDeserializationBlocked(String xml, String message) {
        Exception ex = assertThrows(Exception.class,
                () -> xstream.fromXML(xml), message);
        boolean isSecurityException =
                ex instanceof ForbiddenClassException
                || ex instanceof CannotResolveClassException;
        assertTrue(isSecurityException,
                message + " — got unexpected exception type: " + ex.getClass().getName());
    }

    // -------------------------------------------------------------------------
    // Helper: a simple value type used as a stand-in for real b3meter types.
    // Registered explicitly via allowTypes() in tests that need round-trip.
    // -------------------------------------------------------------------------

    static final class AllowedTestBean {
        String value;
        int number;

        @SuppressWarnings("unused") // XStream needs no-arg constructor for deserialization
        AllowedTestBean() {}

        AllowedTestBean(String value, int number) {
            this.value = value;
            this.number = number;
        }
    }
}
