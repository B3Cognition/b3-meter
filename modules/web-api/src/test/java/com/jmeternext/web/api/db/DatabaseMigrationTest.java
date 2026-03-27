package com.jmeternext.web.api.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that all Flyway migrations apply successfully on H2 in-memory.
 *
 * <p>Flyway runs automatically when the Spring context starts.
 * If any migration fails, context startup fails and the test fails before
 * even reaching the assertion body.
 */
@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void allMigrationsApplySuccessfully() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            var tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            var tableNames = new ArrayList<String>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("TEST_PLANS")),
                    "TEST_PLANS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("TEST_PLAN_REVISIONS")),
                    "TEST_PLAN_REVISIONS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("TEST_RUNS")),
                    "TEST_RUNS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("SAMPLE_RESULTS")),
                    "SAMPLE_RESULTS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("WORKERS")),
                    "WORKERS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("WORKER_ASSIGNMENTS")),
                    "WORKER_ASSIGNMENTS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("USERS")),
                    "USERS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("REFRESH_TOKENS")),
                    "REFRESH_TOKENS table must exist after migration");
            assertTrue(tableNames.stream().anyMatch(t -> t.equalsIgnoreCase("PLUGINS")),
                    "PLUGINS table must exist after migration");
        }
    }

    @Test
    void flywayHistoryTableExists() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            var tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            var tableNames = new ArrayList<String>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
            assertTrue(
                    tableNames.stream().anyMatch(t -> t.toLowerCase().contains("flyway_schema_history")),
                    "Flyway schema history table must exist"
            );
        }
    }

    @Test
    void testPlansTableHasRequiredColumns() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();
            // Inserting a minimal row verifies NOT NULL constraints and column existence
            stmt.execute("""
                    INSERT INTO test_plans (id, name, tree_data)
                    VALUES ('test-id-001', 'Migration Smoke Test', '{}')
                    """);
            var rs = stmt.executeQuery("SELECT id, name, owner_id, tree_data, created_at, updated_at, deleted_at FROM test_plans WHERE id = 'test-id-001'");
            assertTrue(rs.next(), "Inserted row must be retrievable");
            // Clean up
            stmt.execute("DELETE FROM test_plans WHERE id = 'test-id-001'");
        }
    }

    @Test
    void testRunsTableHasRequiredColumns() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();
            stmt.execute("""
                    INSERT INTO test_plans (id, name, tree_data)
                    VALUES ('plan-id-001', 'Run Column Test Plan', '{}')
                    """);
            stmt.execute("""
                    INSERT INTO test_runs (id, plan_id)
                    VALUES ('run-id-001', 'plan-id-001')
                    """);
            var rs = stmt.executeQuery("SELECT id, plan_id, status, started_at, ended_at, total_samples, error_count, owner_id FROM test_runs WHERE id = 'run-id-001'");
            assertTrue(rs.next(), "Inserted test_run row must be retrievable");
            // Clean up
            stmt.execute("DELETE FROM test_runs WHERE id = 'run-id-001'");
            stmt.execute("DELETE FROM test_plans WHERE id = 'plan-id-001'");
        }
    }

    @Test
    void workersTableHasRequiredColumns() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();
            stmt.execute("""
                    INSERT INTO workers (id, hostname, port)
                    VALUES ('worker-id-001', 'localhost', 9090)
                    """);
            var rs = stmt.executeQuery("SELECT id, hostname, port, status, last_heartbeat, registered_at FROM workers WHERE id = 'worker-id-001'");
            assertTrue(rs.next(), "Inserted workers row must be retrievable");
            // Clean up
            stmt.execute("DELETE FROM workers WHERE id = 'worker-id-001'");
        }
    }

    @Test
    void usersTableEnforcesUniqueUsername() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            var stmt = conn.createStatement();
            stmt.execute("""
                    INSERT INTO users (id, username, role)
                    VALUES ('user-id-001', 'alice', 'USER')
                    """);
            try {
                stmt.execute("""
                        INSERT INTO users (id, username, role)
                        VALUES ('user-id-002', 'alice', 'ADMIN')
                        """);
                conn.rollback();
                throw new AssertionError("Duplicate username must be rejected by UNIQUE constraint");
            } catch (SQLException e) {
                // Expected: UNIQUE constraint violation
                conn.rollback();
            }
        }
    }

    @Test
    void pluginsTableEnforcesUniqueNameVersion() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            var stmt = conn.createStatement();
            stmt.execute("""
                    INSERT INTO plugins (id, name, version)
                    VALUES ('plugin-id-001', 'my-plugin', '1.0.0')
                    """);
            try {
                stmt.execute("""
                        INSERT INTO plugins (id, name, version)
                        VALUES ('plugin-id-002', 'my-plugin', '1.0.0')
                        """);
                conn.rollback();
                throw new AssertionError("Duplicate name+version must be rejected by UNIQUE constraint");
            } catch (SQLException e) {
                // Expected: UNIQUE constraint violation
                conn.rollback();
            }
        }
    }
}
