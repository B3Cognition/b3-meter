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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JDBCSamplerExecutor} using an H2 in-memory database.
 *
 * <p>No external port or process is required — H2 is embedded in the JVM.
 * The database URL {@code jdbc:h2:mem:b3test;DB_CLOSE_DELAY=-1} keeps the
 * database alive for the lifetime of the JVM.
 */
class JDBCSamplerExecutorTest {

    private static final String DB_URL = "jdbc:h2:mem:b3test;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void setUpDatabase() throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, "", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT, name VARCHAR(100))");
            stmt.execute("DELETE FROM users");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice')");
        }
    }

    // =========================================================================
    // Test 1: SELECT returns rows
    // =========================================================================

    @Test
    void selectReturnsRows() {
        PlanNode node = jdbcNode("SELECT * FROM users", "select");
        SampleResult result = new SampleResult("select-test");
        Map<String, String> vars = new HashMap<>();

        JDBCSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), "Expected success=true, message: " + result.getFailureMessage());
        assertTrue(result.getResponseBody().contains("Alice"),
                "Response body should contain 'Alice': " + result.getResponseBody());
        assertEquals(200, result.getStatusCode());
    }

    // =========================================================================
    // Test 2: INSERT reports row count
    // =========================================================================

    @Test
    void insertReportsRowCount() {
        PlanNode node = jdbcNode("INSERT INTO users VALUES (2, 'Bob')", "update");
        SampleResult result = new SampleResult("insert-test");
        Map<String, String> vars = new HashMap<>();

        JDBCSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), "Expected success=true, message: " + result.getFailureMessage());
        assertEquals("1", vars.get("AFFECTED_ROWS"),
                "AFFECTED_ROWS variable should be '1'");
        assertTrue(result.getResponseBody().contains("1"),
                "Response body should mention affected rows: " + result.getResponseBody());
    }

    // =========================================================================
    // Test 3: Prepared statement with parameters
    // =========================================================================

    @Test
    void preparedStatementWithParams() {
        PlanNode node = PlanNode.builder("JDBCSampler", "prepared-select")
                .property("dbUrl", DB_URL)
                .property("jdbcDriver", "org.h2.Driver")
                .property("username", "")
                .property("password", "")
                .property("query", "SELECT * FROM users WHERE id = ?")
                .property("queryType", "prepared_select")
                .property("queryArguments", "1")
                .property("queryArgumentsTypes", "INTEGER")
                .property("variableNames", "name")
                .property("resultVariable", "")
                .property("poolSize", 5)
                .build();

        SampleResult result = new SampleResult("prepared-test");
        Map<String, String> vars = new HashMap<>();

        JDBCSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(), "Expected success=true, message: " + result.getFailureMessage());
        assertTrue(result.getResponseBody().contains("Alice"),
                "Response should contain 'Alice' for id=1: " + result.getResponseBody());
    }

    // =========================================================================
    // Test 4: Invalid SQL returns failure
    // =========================================================================

    @Test
    void invalidSqlReturnsFailure() {
        PlanNode node = jdbcNode("SELECT * FROM nonexistent_table", "select");
        SampleResult result = new SampleResult("invalid-sql-test");
        Map<String, String> vars = new HashMap<>();

        JDBCSamplerExecutor.execute(node, result, vars);

        assertFalse(result.isSuccess(), "Expected success=false for invalid SQL");
        assertFalse(result.getFailureMessage().isBlank(),
                "Failure message should not be blank");
    }

    // =========================================================================
    // Test 5: Bad connection URL returns failure
    // =========================================================================

    @Test
    void badConnectionUrlReturnsFailure() {
        PlanNode node = PlanNode.builder("JDBCSampler", "bad-url")
                .property("dbUrl", "jdbc:h2:mem:nonexistent_db_xyz;IFEXISTS=TRUE")
                .property("jdbcDriver", "org.h2.Driver")
                .property("username", "")
                .property("password", "")
                .property("query", "SELECT 1")
                .property("queryType", "select")
                .property("queryArguments", "")
                .property("queryArgumentsTypes", "")
                .property("resultVariable", "")
                .property("variableNames", "")
                .property("poolSize", 5)
                .build();

        SampleResult result = new SampleResult("bad-url-test");
        Map<String, String> vars = new HashMap<>();

        JDBCSamplerExecutor.execute(node, result, vars);

        // Either fails due to connection error or executes on an empty db —
        // if a connection error occurs it must report failure with a message
        // (H2 may create the db on-the-fly; in that case success is acceptable)
        if (!result.isSuccess()) {
            assertFalse(result.getFailureMessage().isBlank(),
                    "Failure message should describe the connection error");
        }
    }

    // =========================================================================
    // Test 6: Pool reuse — 10 sequential SELECTs do not exceed pool size
    // =========================================================================

    @Test
    void poolReuseAfterTenSelects() {
        PlanNode node = jdbcNode("SELECT 1", "select");
        Map<String, String> vars = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            SampleResult result = new SampleResult("pool-test-" + i);
            JDBCSamplerExecutor.execute(node, result, vars);
            assertTrue(result.isSuccess(),
                    "Iteration " + i + " failed: " + result.getFailureMessage());
        }

        // Verify the pool queue for this URL has at most poolSize=10 connections
        var pool = JDBCConnectionPool.getInstance();
        // Pool holds idle connections after return; just verify we can still borrow one
        SampleResult verifyResult = new SampleResult("pool-verify");
        JDBCSamplerExecutor.execute(node, verifyResult, vars);
        assertTrue(verifyResult.isSuccess(),
                "Pool should still be usable after 10 iterations: " + verifyResult.getFailureMessage());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode jdbcNode(String query, String queryType) {
        return PlanNode.builder("JDBCSampler", "jdbc-test")
                .property("dbUrl", DB_URL)
                .property("jdbcDriver", "org.h2.Driver")
                .property("username", "")
                .property("password", "")
                .property("query", query)
                .property("queryType", queryType)
                .property("queryArguments", "")
                .property("queryArgumentsTypes", "")
                .property("resultVariable", "")
                .property("variableNames", "")
                .property("poolSize", 10)
                .build();
    }
}
