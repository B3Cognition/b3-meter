package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes a {@code JDBCDataSource} {@link PlanNode} to store JDBC connection
 * pool configuration in the VU variable map.
 *
 * <p>This is a pure config element: it reads JDBC connection properties from the
 * plan node and stores them as variables for the JDBC sampler to read. No actual
 * database connections are opened at config time.
 *
 * <p>Reads the following JMX properties:
 * <ul>
 *   <li>{@code dataSource} — variable name to reference this connection pool</li>
 *   <li>{@code dbUrl} — JDBC connection URL</li>
 *   <li>{@code driver} — JDBC driver class name</li>
 *   <li>{@code username} — database username</li>
 *   <li>{@code password} — database password</li>
 *   <li>{@code checkQuery} — validation query (default "SELECT 1")</li>
 *   <li>{@code autocommit} — auto-commit mode</li>
 *   <li>{@code maxActive} — maximum active connections</li>
 *   <li>{@code maxWait} — maximum wait for connection (ms)</li>
 *   <li>{@code poolMax} — pool maximum size</li>
 *   <li>{@code timeout} — connection timeout (ms)</li>
 *   <li>{@code transactionIsolation} — transaction isolation level</li>
 *   <li>{@code preinit} — pre-initialize pool at test start</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JDBCConnectionConfigExecutor {

    private static final Logger LOG = Logger.getLogger(JDBCConnectionConfigExecutor.class.getName());

    /** Variable key prefix for JDBC configuration. */
    private static final String JDBC_PREFIX = "__jmn_jdbc_";

    private JDBCConnectionConfigExecutor() {}

    /**
     * Reads JDBC connection properties from the plan node and stores them in
     * the VU variable map, keyed by the data source name.
     *
     * @param node      the JDBCDataSource node; must not be {@code null}
     * @param variables mutable VU variable map
     */
    public static void configure(PlanNode node, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String dataSource = node.getStringProp("dataSource", "");
        if (dataSource.isEmpty()) {
            LOG.log(Level.WARNING,
                    "JDBCConnectionConfigExecutor [{0}]: no dataSource name — skipping",
                    node.getTestName());
            return;
        }

        String prefix = JDBC_PREFIX + dataSource + "_";

        variables.put(prefix + "dbUrl", node.getStringProp("dbUrl", ""));
        variables.put(prefix + "driver", node.getStringProp("driver", ""));
        variables.put(prefix + "username", node.getStringProp("username", ""));
        variables.put(prefix + "password", node.getStringProp("password", ""));
        variables.put(prefix + "checkQuery", node.getStringProp("checkQuery", "SELECT 1"));
        variables.put(prefix + "autocommit", node.getStringProp("autocommit", "true"));
        variables.put(prefix + "maxActive", node.getStringProp("maxActive", "10"));
        variables.put(prefix + "maxWait", node.getStringProp("maxWait", "10000"));
        variables.put(prefix + "poolMax", node.getStringProp("poolMax", "10"));
        variables.put(prefix + "timeout", node.getStringProp("timeout", "10000"));
        variables.put(prefix + "transactionIsolation",
                node.getStringProp("transactionIsolation", "DEFAULT"));
        variables.put(prefix + "keepAlive", node.getStringProp("keepAlive", "true"));
        variables.put(prefix + "connectionAge", node.getStringProp("connectionAge", "5000"));
        variables.put(prefix + "connectionProperties",
                node.getStringProp("connectionProperties", ""));
        variables.put(prefix + "initQuery", node.getStringProp("initQuery", ""));
        variables.put(prefix + "preinit", String.valueOf(node.getBoolProp("preinit", false)));

        // Mark this data source as configured
        variables.put(prefix + "configured", "true");

        LOG.log(Level.FINE,
                "JDBCConnectionConfigExecutor [{0}]: configured data source '{1}' -> {2}",
                new Object[]{node.getTestName(), dataSource,
                        node.getStringProp("dbUrl", "")});
    }

    /**
     * Retrieves a JDBC configuration property for a given data source.
     *
     * @param dataSource the data source name
     * @param property   the property name (e.g., "dbUrl", "driver")
     * @param variables  VU variable map
     * @return the property value, or {@code null} if not configured
     */
    public static String getProperty(String dataSource, String property,
                                       Map<String, String> variables) {
        return variables.get(JDBC_PREFIX + dataSource + "_" + property);
    }

    /**
     * Checks whether a data source has been configured.
     *
     * @param dataSource the data source name
     * @param variables  VU variable map
     * @return {@code true} if configured
     */
    public static boolean isConfigured(String dataSource, Map<String, String> variables) {
        return "true".equals(variables.get(JDBC_PREFIX + dataSource + "_configured"));
    }
}
