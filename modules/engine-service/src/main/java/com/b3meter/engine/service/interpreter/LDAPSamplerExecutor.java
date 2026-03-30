package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

/**
 * Executes an {@code LDAPSampler} {@link PlanNode} using {@link InitialLdapContext} (JDK built-in).
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code LDAPSampler.host} — LDAP server hostname</li>
 *   <li>{@code LDAPSampler.port} — port (default 389)</li>
 *   <li>{@code LDAPSampler.baseDN} — base distinguished name</li>
 *   <li>{@code LDAPSampler.bindDN} — bind DN for authentication</li>
 *   <li>{@code LDAPSampler.bindPassword} — bind password</li>
 *   <li>{@code LDAPSampler.searchFilter} — LDAP search filter, e.g. "(uid=jdoe)"</li>
 *   <li>{@code LDAPSampler.action} — "search", "bind", "add", "modify", or "delete"</li>
 *   <li>{@code LDAPSampler.timeout} — timeout in ms (default 10000)</li>
 *   <li>{@code LDAPSampler.entryDN} — DN for add/modify/delete operations</li>
 *   <li>{@code LDAPSampler.attributes} — semicolon-separated key=value pairs for add/modify</li>
 * </ul>
 *
 * <p>Uses {@code javax.naming.ldap.InitialLdapContext} which is part of the JDK.
 * No external LDAP library is used (Constitution Principle I: framework-free).
 */
public final class LDAPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(LDAPSamplerExecutor.class.getName());

    private static final int DEFAULT_PORT = 389;
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private LDAPSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the LDAP operation described by {@code node}.
     *
     * @param node      the LDAPSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String host = resolve(node.getStringProp("LDAPSampler.host", ""), variables);
        int port = node.getIntProp("LDAPSampler.port", DEFAULT_PORT);
        String baseDN = resolve(node.getStringProp("LDAPSampler.baseDN", ""), variables);
        String bindDN = resolve(node.getStringProp("LDAPSampler.bindDN", ""), variables);
        String bindPassword = resolve(node.getStringProp("LDAPSampler.bindPassword", ""), variables);
        String searchFilter = resolve(node.getStringProp("LDAPSampler.searchFilter", "(objectClass=*)"), variables);
        String action = resolve(node.getStringProp("LDAPSampler.action", "search"), variables).toLowerCase();
        int timeout = node.getIntProp("LDAPSampler.timeout", DEFAULT_TIMEOUT_MS);
        String entryDN = resolve(node.getStringProp("LDAPSampler.entryDN", ""), variables);
        String attributes = resolve(node.getStringProp("LDAPSampler.attributes", ""), variables);

        if (host.isBlank()) {
            result.setFailureMessage("LDAPSampler.host is empty");
            return;
        }

        LOG.log(Level.FINE, "LDAPSamplerExecutor: {0} on {1}:{2} baseDN={3}",
                new Object[]{action, host, port, baseDN});

        long start = System.currentTimeMillis();
        Hashtable<String, String> env = buildEnvironment(host, port, bindDN, bindPassword, timeout);

        InitialLdapContext ctx = null;
        try {
            ctx = new InitialLdapContext(env, null);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);
            result.setLatencyMs(connectTime);

            switch (action) {
                case "bind" -> {
                    // Creating the context IS the bind test; if we got here, it succeeded
                    result.setStatusCode(0);
                    result.setResponseBody("LDAP bind successful for " + bindDN);
                }

                case "search" -> {
                    String searchResult = executeSearch(ctx, baseDN, searchFilter, timeout);
                    result.setStatusCode(0);
                    result.setResponseBody(searchResult);
                }

                case "add" -> {
                    executeAdd(ctx, entryDN, attributes);
                    result.setStatusCode(0);
                    result.setResponseBody("Added entry: " + entryDN);
                }

                case "modify" -> {
                    executeModify(ctx, entryDN, attributes);
                    result.setStatusCode(0);
                    result.setResponseBody("Modified entry: " + entryDN);
                }

                case "delete" -> {
                    ctx.destroySubcontext(entryDN);
                    result.setStatusCode(0);
                    result.setResponseBody("Deleted entry: " + entryDN);
                }

                default -> {
                    result.setFailureMessage("LDAP unsupported action: " + action);
                    return;
                }
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

        } catch (AuthenticationException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setStatusCode(49); // LDAP error code 49 = invalid credentials
            result.setFailureMessage("LDAP authentication failed: " + e.getMessage());
            LOG.log(Level.WARNING, "LDAPSamplerExecutor: auth error for " + host, e);
        } catch (NamingException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("LDAP error: " + e.getMessage());
            LOG.log(Level.WARNING, "LDAPSamplerExecutor: error for " + host, e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    // =========================================================================
    // LDAP operation implementations
    // =========================================================================

    /**
     * Executes an LDAP search and returns results as formatted text.
     */
    private static String executeSearch(InitialLdapContext ctx, String baseDN,
                                         String searchFilter, int timeout) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setTimeLimit(timeout);
        controls.setCountLimit(1000); // reasonable limit

        NamingEnumeration<SearchResult> results = ctx.search(baseDN, searchFilter, controls);

        StringBuilder sb = new StringBuilder();
        int count = 0;

        while (results.hasMore()) {
            SearchResult sr = results.next();
            sb.append("dn: ").append(sr.getNameInNamespace()).append('\n');

            Attributes attrs = sr.getAttributes();
            NamingEnumeration<? extends Attribute> attrEnum = attrs.getAll();
            while (attrEnum.hasMore()) {
                Attribute attr = attrEnum.next();
                for (int i = 0; i < attr.size(); i++) {
                    sb.append(attr.getID()).append(": ").append(attr.get(i)).append('\n');
                }
            }
            sb.append('\n');
            count++;
        }

        if (count == 0) {
            sb.append("No results found for filter: ").append(searchFilter);
        } else {
            sb.append("Total entries: ").append(count);
        }

        return sb.toString();
    }

    /**
     * Adds a new LDAP entry.
     */
    private static void executeAdd(InitialLdapContext ctx, String entryDN,
                                    String attributesStr) throws NamingException {
        BasicAttributes attrs = parseAttributes(attributesStr);
        ctx.createSubcontext(entryDN, attrs);
    }

    /**
     * Modifies an existing LDAP entry.
     */
    private static void executeModify(InitialLdapContext ctx, String entryDN,
                                       String attributesStr) throws NamingException {
        BasicAttributes attrs = parseAttributes(attributesStr);
        NamingEnumeration<? extends Attribute> attrEnum = attrs.getAll();

        while (attrEnum.hasMore()) {
            Attribute attr = attrEnum.next();
            ModificationItem mod = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attr);
            ctx.modifyAttributes(entryDN, new ModificationItem[]{mod});
        }
    }

    // =========================================================================
    // Helpers (package-visible for testing)
    // =========================================================================

    /**
     * Builds the JNDI environment hashtable for LDAP connection.
     *
     * @param host     LDAP server hostname
     * @param port     LDAP server port
     * @param bindDN   bind distinguished name
     * @param password bind password
     * @param timeout  connection timeout in ms
     * @return the environment hashtable
     */
    static Hashtable<String, String> buildEnvironment(String host, int port,
                                                       String bindDN, String password,
                                                       int timeout) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);

        if (bindDN != null && !bindDN.isBlank()) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, bindDN);
            env.put(Context.SECURITY_CREDENTIALS, password != null ? password : "");
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        }

        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(timeout));

        return env;
    }

    /**
     * Parses semicolon-separated key=value attribute pairs into LDAP BasicAttributes.
     *
     * <p>Format: {@code "cn=John Doe;mail=jdoe@example.com;objectClass=inetOrgPerson"}
     *
     * @param attributesStr the semicolon-separated attributes string
     * @return parsed LDAP attributes
     */
    static BasicAttributes parseAttributes(String attributesStr) {
        BasicAttributes attrs = new BasicAttributes(true); // case-insensitive
        if (attributesStr == null || attributesStr.isBlank()) {
            return attrs;
        }

        for (String pair : attributesStr.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;

            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                String key = trimmed.substring(0, eqIdx).trim();
                String value = trimmed.substring(eqIdx + 1).trim();
                attrs.put(new BasicAttribute(key, value));
            }
        }
        return attrs;
    }

    /**
     * Formats the LDAP URL from host and port. Visible for testing.
     *
     * @param host LDAP server hostname
     * @param port LDAP server port
     * @return the LDAP URL string
     */
    static String formatLdapUrl(String host, int port) {
        return "ldap://" + host + ":" + port;
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
