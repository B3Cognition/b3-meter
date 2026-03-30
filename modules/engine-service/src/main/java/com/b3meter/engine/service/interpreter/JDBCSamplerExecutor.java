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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code JDBCSampler} {@link PlanNode} using JDBC.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code dbUrl} — JDBC connection URL (e.g. {@code jdbc:postgresql://host:5432/db})</li>
 *   <li>{@code jdbcDriver} — driver class name (e.g. {@code org.postgresql.Driver})</li>
 *   <li>{@code username} — database username</li>
 *   <li>{@code password} — database password</li>
 *   <li>{@code query} — SQL query to execute</li>
 *   <li>{@code queryType} — one of: {@code select}, {@code update}, {@code callable},
 *       {@code prepared_select}, {@code prepared_update}</li>
 *   <li>{@code queryArguments} — comma-separated values for prepared statements</li>
 *   <li>{@code queryArgumentsTypes} — comma-separated JDBC types (VARCHAR, INTEGER, etc.)</li>
 *   <li>{@code resultVariable} — variable name to store full result set (optional)</li>
 *   <li>{@code variableNames} — comma-separated column names to extract as variables</li>
 *   <li>{@code poolSize} — connection pool size (default 10)</li>
 * </ul>
 *
 * <p>Uses a simple connection pool ({@link JDBCConnectionPool}) for connection reuse.
 * Only JDK types are used — no external JDBC pool library (Constitution Principle I).
 */
public final class JDBCSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(JDBCSamplerExecutor.class.getName());

    /** Maximum number of result rows to include in the response body. */
    private static final int MAX_RESPONSE_ROWS = 500;

    private static final int DEFAULT_POOL_SIZE = 10;

    private JDBCSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the JDBC operation described by {@code node}.
     *
     * @param node      the JDBCSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String dbUrl = resolve(node.getStringProp("dbUrl", ""), variables);
        String jdbcDriver = resolve(node.getStringProp("jdbcDriver", ""), variables);
        String username = resolve(node.getStringProp("username", ""), variables);
        String password = resolve(node.getStringProp("password", ""), variables);
        String query = resolve(node.getStringProp("query", ""), variables);
        String queryType = resolve(node.getStringProp("queryType", "select"), variables)
                .toLowerCase().trim();
        String queryArguments = resolve(node.getStringProp("queryArguments", ""), variables);
        String queryArgumentsTypes = resolve(node.getStringProp("queryArgumentsTypes", ""), variables);
        String resultVariable = resolve(node.getStringProp("resultVariable", ""), variables);
        String variableNames = resolve(node.getStringProp("variableNames", ""), variables);
        int poolSize = node.getIntProp("poolSize", DEFAULT_POOL_SIZE);

        if (dbUrl.isBlank()) {
            result.setFailureMessage("JDBCSampler.dbUrl is empty");
            return;
        }
        if (query.isBlank()) {
            result.setFailureMessage("JDBCSampler.query is empty");
            return;
        }

        // Load JDBC driver class if specified
        if (!jdbcDriver.isBlank()) {
            try {
                Class.forName(jdbcDriver);
            } catch (ClassNotFoundException e) {
                result.setFailureMessage("JDBC driver not found: " + jdbcDriver);
                return;
            }
        }

        LOG.log(Level.FINE, "JDBCSamplerExecutor: {0} on {1}", new Object[]{queryType, dbUrl});

        long start = System.currentTimeMillis();
        Connection conn = null;

        try {
            conn = JDBCConnectionPool.getInstance().getConnection(dbUrl, username, password, poolSize);
            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            switch (queryType) {
                case "select" -> executeSelect(conn, query, result, variables,
                        variableNames, resultVariable);

                case "update" -> executeUpdate(conn, query, result, variables);

                case "callable" -> executeCallable(conn, query, result, variables,
                        variableNames, resultVariable);

                case "prepared_select" -> executePreparedSelect(conn, query,
                        queryArguments, queryArgumentsTypes,
                        result, variables, variableNames, resultVariable);

                case "prepared_update" -> executePreparedUpdate(conn, query,
                        queryArguments, queryArgumentsTypes,
                        result, variables);

                default -> {
                    result.setFailureMessage("Unknown queryType: " + queryType);
                    return;
                }
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setStatusCode(200);

        } catch (SQLException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setStatusCode(e.getErrorCode());
            result.setFailureMessage("JDBC error: " + e.getMessage()
                    + " [SQLState=" + e.getSQLState() + "]");
            LOG.log(Level.WARNING, "JDBCSamplerExecutor: error for " + dbUrl, e);
        } finally {
            if (conn != null) {
                JDBCConnectionPool.getInstance().returnConnection(dbUrl, conn);
            }
        }
    }

    // =========================================================================
    // Query type implementations
    // =========================================================================

    /**
     * Executes a plain SELECT statement.
     */
    private static void executeSelect(Connection conn, String query,
                                       SampleResult result, Map<String, String> variables,
                                       String variableNames, String resultVariable)
            throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            long latency = System.currentTimeMillis();
            result.setLatencyMs(latency - (result.getConnectTimeMs() > 0 ? latency : 0));

            processResultSet(rs, result, variables, variableNames, resultVariable);
        }
    }

    /**
     * Executes a plain UPDATE/INSERT/DELETE statement.
     */
    private static void executeUpdate(Connection conn, String query,
                                       SampleResult result, Map<String, String> variables)
            throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            int affected = stmt.executeUpdate(query);
            result.setResponseBody("Affected rows: " + affected);
            variables.put("AFFECTED_ROWS", String.valueOf(affected));
        }
    }

    /**
     * Executes a callable statement (stored procedure).
     */
    private static void executeCallable(Connection conn, String query,
                                         SampleResult result, Map<String, String> variables,
                                         String variableNames, String resultVariable)
            throws SQLException {
        try (CallableStatement cstmt = conn.prepareCall(query)) {
            boolean hasResultSet = cstmt.execute();
            if (hasResultSet) {
                try (ResultSet rs = cstmt.getResultSet()) {
                    processResultSet(rs, result, variables, variableNames, resultVariable);
                }
            } else {
                int affected = cstmt.getUpdateCount();
                result.setResponseBody("Affected rows: " + affected);
                variables.put("AFFECTED_ROWS", String.valueOf(affected));
            }
        }
    }

    /**
     * Executes a prepared SELECT statement with parameterized arguments.
     */
    private static void executePreparedSelect(Connection conn, String query,
                                               String queryArguments, String queryArgumentsTypes,
                                               SampleResult result, Map<String, String> variables,
                                               String variableNames, String resultVariable)
            throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, queryArguments, queryArgumentsTypes);

            try (ResultSet rs = pstmt.executeQuery()) {
                processResultSet(rs, result, variables, variableNames, resultVariable);
            }
        }
    }

    /**
     * Executes a prepared UPDATE/INSERT/DELETE statement with parameterized arguments.
     */
    private static void executePreparedUpdate(Connection conn, String query,
                                               String queryArguments, String queryArgumentsTypes,
                                               SampleResult result, Map<String, String> variables)
            throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            setParameters(pstmt, queryArguments, queryArgumentsTypes);

            int affected = pstmt.executeUpdate();
            result.setResponseBody("Affected rows: " + affected);
            variables.put("AFFECTED_ROWS", String.valueOf(affected));
        }
    }

    // =========================================================================
    // Result set processing
    // =========================================================================

    /**
     * Iterates a {@link ResultSet}, builds a text response body (tab-separated),
     * and extracts variables according to {@code variableNames}.
     *
     * @param rs             the result set to process
     * @param result         sample result to populate with response body
     * @param variables      VU variables map for extracted values
     * @param variableNames  comma-separated column names to extract as variables
     * @param resultVariable optional variable name to store full result text
     */
    private static void processResultSet(ResultSet rs, SampleResult result,
                                          Map<String, String> variables,
                                          String variableNames, String resultVariable)
            throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // Parse variable names to extract
        String[] varNames = variableNames.isBlank()
                ? new String[0]
                : variableNames.split(",");

        StringBuilder body = new StringBuilder();

        // Header row
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) body.append('\t');
            body.append(meta.getColumnLabel(i));
        }
        body.append('\n');

        int rowIndex = 0;
        while (rs.next() && rowIndex < MAX_RESPONSE_ROWS) {
            rowIndex++;

            // Build row text
            StringBuilder row = new StringBuilder();
            for (int col = 1; col <= columnCount; col++) {
                if (col > 1) row.append('\t');
                String val = rs.getString(col);
                row.append(val != null ? val : "NULL");
            }
            body.append(row).append('\n');

            // Extract variables for the first row only (JMeter convention)
            if (rowIndex == 1) {
                for (String varName : varNames) {
                    String trimmed = varName.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            String val = rs.getString(trimmed);
                            variables.put(trimmed, val != null ? val : "");
                        } catch (SQLException e) {
                            // Column not found — try by label match in metadata
                            LOG.log(Level.FINE, "Variable column not found: {0}", trimmed);
                        }
                    }
                }
            }

            // Store per-row variables with _N suffix (JMeter convention)
            for (String varName : varNames) {
                String trimmed = varName.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        String val = rs.getString(trimmed);
                        variables.put(trimmed + "_" + rowIndex, val != null ? val : "");
                    } catch (SQLException e) {
                        // ignore — column may not exist
                    }
                }
            }
        }

        // Store row count
        for (String varName : varNames) {
            String trimmed = varName.trim();
            if (!trimmed.isEmpty()) {
                variables.put(trimmed + "_#", String.valueOf(rowIndex));
            }
        }

        String bodyText = body.toString();
        result.setResponseBody(bodyText);

        // Store full result in named variable if requested
        if (!resultVariable.isBlank()) {
            variables.put(resultVariable, bodyText);
        }
    }

    // =========================================================================
    // Prepared statement parameter handling
    // =========================================================================

    /**
     * Sets parameters on a {@link PreparedStatement} using the comma-separated
     * argument values and their corresponding JDBC type names.
     *
     * @param pstmt              the prepared statement
     * @param queryArguments     comma-separated parameter values
     * @param queryArgumentsTypes comma-separated JDBC type names (VARCHAR, INTEGER, etc.)
     */
    private static void setParameters(PreparedStatement pstmt, String queryArguments,
                                       String queryArgumentsTypes) throws SQLException {
        if (queryArguments.isBlank()) return;

        String[] args = queryArguments.split(",", -1);
        String[] types = queryArgumentsTypes.isBlank()
                ? new String[0]
                : queryArgumentsTypes.split(",", -1);

        for (int i = 0; i < args.length; i++) {
            String value = args[i].trim();
            String typeName = (i < types.length) ? types[i].trim().toUpperCase() : "VARCHAR";
            int paramIndex = i + 1;

            if ("NULL".equalsIgnoreCase(value) || "]NULL[".equals(value)) {
                pstmt.setNull(paramIndex, mapSqlType(typeName));
                continue;
            }

            switch (typeName) {
                case "INTEGER", "INT" -> pstmt.setInt(paramIndex, Integer.parseInt(value));
                case "LONG", "BIGINT" -> pstmt.setLong(paramIndex, Long.parseLong(value));
                case "DOUBLE", "FLOAT" -> pstmt.setDouble(paramIndex, Double.parseDouble(value));
                case "BOOLEAN", "BIT" -> pstmt.setBoolean(paramIndex, Boolean.parseBoolean(value));
                case "DATE" -> pstmt.setDate(paramIndex, java.sql.Date.valueOf(value));
                case "TIMESTAMP" -> pstmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(value));
                case "DECIMAL", "NUMERIC" -> pstmt.setBigDecimal(paramIndex,
                        new java.math.BigDecimal(value));
                default -> pstmt.setString(paramIndex, value);  // VARCHAR and everything else
            }
        }
    }

    /**
     * Maps a JDBC type name string to the corresponding {@link Types} constant.
     *
     * @param typeName the type name (e.g. "VARCHAR", "INTEGER")
     * @return the {@link Types} constant
     */
    static int mapSqlType(String typeName) {
        return switch (typeName.toUpperCase()) {
            case "INTEGER", "INT" -> Types.INTEGER;
            case "LONG", "BIGINT" -> Types.BIGINT;
            case "DOUBLE" -> Types.DOUBLE;
            case "FLOAT" -> Types.FLOAT;
            case "BOOLEAN", "BIT" -> Types.BOOLEAN;
            case "DATE" -> Types.DATE;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            case "DECIMAL", "NUMERIC" -> Types.DECIMAL;
            case "BLOB" -> Types.BLOB;
            case "CLOB" -> Types.CLOB;
            default -> Types.VARCHAR;
        };
    }

    /**
     * Resolves {@code ${varName}} placeholders in a string value.
     */
    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
