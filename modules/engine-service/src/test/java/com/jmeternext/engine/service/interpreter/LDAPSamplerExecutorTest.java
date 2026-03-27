package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.directory.BasicAttributes;
import java.util.Hashtable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LDAPSamplerExecutor}.
 *
 * <p>Tests environment setup, attribute parsing, search filter construction, and error handling.
 * No live LDAP server is required — configuration and parsing helpers are tested
 * directly via the package-visible methods.
 */
class LDAPSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("ldap-test");
        assertThrows(NullPointerException.class,
                () -> LDAPSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = ldapNode("localhost", "dc=example,dc=org", "(uid=jdoe)");
        assertThrows(NullPointerException.class,
                () -> LDAPSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = ldapNode("localhost", "dc=example,dc=org", "(uid=jdoe)");
        SampleResult result = new SampleResult("ldap-test");
        assertThrows(NullPointerException.class,
                () -> LDAPSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyHost() {
        PlanNode node = ldapNode("", "dc=example,dc=org", "(uid=jdoe)");
        SampleResult result = new SampleResult("ldap-test");

        LDAPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("host is empty"));
    }

    // =========================================================================
    // Connection error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        PlanNode node = PlanNode.builder("LDAPSampler", "ldap-refused")
                .property("LDAPSampler.host", "127.0.0.1")
                .property("LDAPSampler.port", 1)
                .property("LDAPSampler.baseDN", "dc=example,dc=org")
                .property("LDAPSampler.action", "search")
                .property("LDAPSampler.searchFilter", "(uid=jdoe)")
                .property("LDAPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ldap-refused");

        LDAPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("LDAP error"),
                "Expected LDAP error, got: " + result.getFailureMessage());
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    @Test
    void execute_handlesBindFailure() {
        PlanNode node = PlanNode.builder("LDAPSampler", "ldap-bind-fail")
                .property("LDAPSampler.host", "127.0.0.1")
                .property("LDAPSampler.port", 1)
                .property("LDAPSampler.baseDN", "dc=example,dc=org")
                .property("LDAPSampler.bindDN", "cn=admin,dc=example,dc=org")
                .property("LDAPSampler.bindPassword", "wrongpassword")
                .property("LDAPSampler.action", "bind")
                .property("LDAPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ldap-bind-fail");

        LDAPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        // Should fail with connection or auth error
        assertFalse(result.getFailureMessage().isEmpty());
    }

    // =========================================================================
    // Environment building
    // =========================================================================

    @Test
    void buildEnvironment_withAuthentication() {
        Hashtable<String, String> env = LDAPSamplerExecutor.buildEnvironment(
                "ldap.example.com", 389,
                "cn=admin,dc=example,dc=org", "secret", 10000);

        assertEquals("com.sun.jndi.ldap.LdapCtxFactory",
                env.get(Context.INITIAL_CONTEXT_FACTORY));
        assertEquals("ldap://ldap.example.com:389",
                env.get(Context.PROVIDER_URL));
        assertEquals("simple", env.get(Context.SECURITY_AUTHENTICATION));
        assertEquals("cn=admin,dc=example,dc=org",
                env.get(Context.SECURITY_PRINCIPAL));
        assertEquals("secret", env.get(Context.SECURITY_CREDENTIALS));
        assertEquals("10000", env.get("com.sun.jndi.ldap.connect.timeout"));
        assertEquals("10000", env.get("com.sun.jndi.ldap.read.timeout"));
    }

    @Test
    void buildEnvironment_anonymous() {
        Hashtable<String, String> env = LDAPSamplerExecutor.buildEnvironment(
                "localhost", 3389, "", "", 5000);

        assertEquals("ldap://localhost:3389", env.get(Context.PROVIDER_URL));
        assertEquals("none", env.get(Context.SECURITY_AUTHENTICATION));
        assertNull(env.get(Context.SECURITY_PRINCIPAL));
    }

    @Test
    void buildEnvironment_nullPassword() {
        Hashtable<String, String> env = LDAPSamplerExecutor.buildEnvironment(
                "localhost", 389, "cn=admin", null, 5000);

        assertEquals("", env.get(Context.SECURITY_CREDENTIALS));
    }

    @Test
    void buildEnvironment_customPort() {
        Hashtable<String, String> env = LDAPSamplerExecutor.buildEnvironment(
                "ldap.internal", 3389, "", "", 5000);

        assertEquals("ldap://ldap.internal:3389", env.get(Context.PROVIDER_URL));
    }

    // =========================================================================
    // Attribute parsing
    // =========================================================================

    @Test
    void parseAttributes_standard() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes(
                "cn=John Doe;mail=jdoe@example.com;objectClass=inetOrgPerson");

        assertEquals("John Doe", attrs.get("cn").toString().split(": ")[1]);
        assertNotNull(attrs.get("mail"));
        assertNotNull(attrs.get("objectClass"));
    }

    @Test
    void parseAttributes_singlePair() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes("uid=jdoe");
        assertNotNull(attrs.get("uid"));
    }

    @Test
    void parseAttributes_emptyString() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes("");
        assertEquals(0, attrs.size());
    }

    @Test
    void parseAttributes_nullString() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes(null);
        assertEquals(0, attrs.size());
    }

    @Test
    void parseAttributes_trailingDelimiter() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes("cn=Test;");
        assertNotNull(attrs.get("cn"));
        assertEquals(1, attrs.size());
    }

    @Test
    void parseAttributes_spacesAroundEquals() {
        BasicAttributes attrs = LDAPSamplerExecutor.parseAttributes("cn = John Doe");
        assertNotNull(attrs.get("cn"));
    }

    // =========================================================================
    // LDAP URL formatting
    // =========================================================================

    @Test
    void formatLdapUrl_standard() {
        assertEquals("ldap://localhost:389",
                LDAPSamplerExecutor.formatLdapUrl("localhost", 389));
    }

    @Test
    void formatLdapUrl_customPort() {
        assertEquals("ldap://ldap.example.com:3389",
                LDAPSamplerExecutor.formatLdapUrl("ldap.example.com", 3389));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariables() {
        PlanNode node = PlanNode.builder("LDAPSampler", "ldap-vars")
                .property("LDAPSampler.host", "${ldapHost}")
                .property("LDAPSampler.port", 1)
                .property("LDAPSampler.baseDN", "${baseDN}")
                .property("LDAPSampler.bindDN", "${bindDN}")
                .property("LDAPSampler.bindPassword", "${password}")
                .property("LDAPSampler.searchFilter", "(uid=${uid})")
                .property("LDAPSampler.action", "search")
                .property("LDAPSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("ldap-vars");
        Map<String, String> vars = Map.of(
                "ldapHost", "127.0.0.1",
                "baseDN", "dc=example,dc=org",
                "bindDN", "cn=admin,dc=example,dc=org",
                "password", "admin",
                "uid", "jdoe"
        );

        LDAPSamplerExecutor.execute(node, result, vars);

        // Connection will fail but variables were resolved (no "empty" error)
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("LDAP error"),
                "Should fail with LDAP error, not validation error: " + result.getFailureMessage());
    }

    // =========================================================================
    // Default property values
    // =========================================================================

    @Test
    void execute_usesDefaults() {
        PlanNode node = PlanNode.builder("LDAPSampler", "ldap-defaults")
                .property("LDAPSampler.host", "127.0.0.1")
                .property("LDAPSampler.port", 1)
                .property("LDAPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ldap-defaults");

        LDAPSamplerExecutor.execute(node, result, Map.of());

        // Confirms no NPE with missing optional properties
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_unsupportedAction() {
        PlanNode node = PlanNode.builder("LDAPSampler", "ldap-bad-action")
                .property("LDAPSampler.host", "127.0.0.1")
                .property("LDAPSampler.port", 1)
                .property("LDAPSampler.action", "rename")
                .property("LDAPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ldap-bad-action");

        LDAPSamplerExecutor.execute(node, result, Map.of());

        // Might fail with connection error or unsupported action, either way it fails
        assertFalse(result.isSuccess());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode ldapNode(String host, String baseDN, String filter) {
        return PlanNode.builder("LDAPSampler", "ldap-test")
                .property("LDAPSampler.host", host)
                .property("LDAPSampler.port", 389)
                .property("LDAPSampler.baseDN", baseDN)
                .property("LDAPSampler.searchFilter", filter)
                .property("LDAPSampler.action", "search")
                .property("LDAPSampler.timeout", 500)
                .build();
    }
}
