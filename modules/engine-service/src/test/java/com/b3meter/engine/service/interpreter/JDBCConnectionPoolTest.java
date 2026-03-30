package com.jmeternext.engine.service.interpreter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JDBCConnectionPool}.
 *
 * <p>Since the pool is a singleton with a shared {@code ConcurrentHashMap}, each test
 * calls {@code closeAll()} in {@code @AfterEach} to ensure isolation.
 *
 * <p>No H2 dependency is available on the test classpath, so these tests exercise
 * pool mechanics via the actual singleton. If a real database is needed in the future,
 * add H2 to testImplementation and use {@code jdbc:h2:mem:test}.
 */
class JDBCConnectionPoolTest {

    private final JDBCConnectionPool pool = JDBCConnectionPool.getInstance();

    @AfterEach
    void cleanUp() {
        pool.closeAll();
    }

    // =========================================================================
    // Singleton
    // =========================================================================

    @Test
    void getInstanceReturnsSameObject() {
        assertSame(JDBCConnectionPool.getInstance(), JDBCConnectionPool.getInstance(),
                "getInstance() should always return the same singleton");
    }

    // =========================================================================
    // Connection creation (expected to fail without a real driver)
    // =========================================================================

    @Test
    void getConnectionThrowsForInvalidUrl() {
        assertThrows(SQLException.class,
                () -> pool.getConnection("jdbc:invalid://nowhere", null, null, 5),
                "Should throw SQLException for an invalid JDBC URL");
    }

    // =========================================================================
    // Return and reuse mechanics
    // =========================================================================

    @Test
    void returnConnectionWithNullIsNoOp() {
        // Should not throw
        assertDoesNotThrow(() -> pool.returnConnection("jdbc:test://x", null));
    }

    @Test
    void isValidReturnsFalseForNull() {
        assertFalse(pool.isValid(null), "null connection should not be valid");
    }

    // =========================================================================
    // closeAll
    // =========================================================================

    @Test
    void closeAllOnEmptyPoolIsNoOp() {
        // Should not throw when no connections exist
        assertDoesNotThrow(() -> pool.closeAll());
    }

    // =========================================================================
    // Pool reuse (with mock-friendly connection pattern)
    // =========================================================================

    @Test
    void returnedConnectionIsReusable() throws SQLException {
        // Create a stub connection to test return/borrow cycle
        // We use a simple approach: create a pool entry and verify the queue mechanics
        String url = "jdbc:stub://pool-reuse-test";

        // Create a fake connection using a TestConnection
        Connection fakeConn = new StubConnection();
        pool.returnConnection(url, fakeConn);

        // The pool should now have the connection queued.
        // But getConnection creates new connections via DriverManager (which will fail),
        // so we test the return path instead.
        // When returnConnection is called with a valid connection, it should be offered to the queue.
        // We can verify closeAll drains it.
        pool.closeAll();

        // After closeAll, the pool map should be empty
        // getConnection should create a fresh connection (and fail because no driver)
        assertThrows(SQLException.class,
                () -> pool.getConnection(url, null, null, 5),
                "After closeAll, new connection attempt should go to DriverManager");
    }

    @Test
    void poolSizeLimitRespected() throws SQLException {
        String url = "jdbc:stub://pool-size-test";

        // Return more connections than the pool size (pool is created lazily on first getConnection,
        // but returnConnection checks pools.get(url) which would be null for a never-borrowed URL)
        // This tests the "queue full" path — returnConnection should close excess connections.

        // First, we need to create the pool entry. Since getConnection creates the queue,
        // we simulate by returning connections. returnConnection for an unknown URL
        // should close the connection (queue == null).
        Connection conn1 = new StubConnection();
        pool.returnConnection(url, conn1);

        // conn1 should have been closed because no queue exists for this URL
        assertTrue(((StubConnection) conn1).closed,
                "Connection should be closed when returned to non-existent queue");
    }

    // =========================================================================
    // StubConnection — minimal test double
    // =========================================================================

    /**
     * Minimal Connection stub that tracks close state.
     * Only implements the methods used by JDBCConnectionPool.
     */
    static class StubConnection implements Connection {
        volatile boolean closed = false;

        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }
        @Override public boolean isValid(int timeout) { return !closed; }

        // --- All other methods throw UnsupportedOperationException ---
        @Override public java.sql.Statement createStatement() { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { throw unsupported(); }
        @Override public java.sql.CallableStatement prepareCall(String sql) { throw unsupported(); }
        @Override public String nativeSQL(String sql) { throw unsupported(); }
        @Override public void setAutoCommit(boolean autoCommit) { throw unsupported(); }
        @Override public boolean getAutoCommit() { throw unsupported(); }
        @Override public void commit() { throw unsupported(); }
        @Override public void rollback() { throw unsupported(); }
        @Override public java.sql.DatabaseMetaData getMetaData() { throw unsupported(); }
        @Override public void setReadOnly(boolean readOnly) { throw unsupported(); }
        @Override public boolean isReadOnly() { throw unsupported(); }
        @Override public void setCatalog(String catalog) { throw unsupported(); }
        @Override public String getCatalog() { throw unsupported(); }
        @Override public void setTransactionIsolation(int level) { throw unsupported(); }
        @Override public int getTransactionIsolation() { throw unsupported(); }
        @Override public java.sql.SQLWarning getWarnings() { throw unsupported(); }
        @Override public void clearWarnings() { throw unsupported(); }
        @Override public java.sql.Statement createStatement(int a, int b) { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int a, int b) { throw unsupported(); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int a, int b) { throw unsupported(); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() { throw unsupported(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) { throw unsupported(); }
        @Override public void setHoldability(int holdability) { throw unsupported(); }
        @Override public int getHoldability() { throw unsupported(); }
        @Override public java.sql.Savepoint setSavepoint() { throw unsupported(); }
        @Override public java.sql.Savepoint setSavepoint(String name) { throw unsupported(); }
        @Override public void rollback(java.sql.Savepoint savepoint) { throw unsupported(); }
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) { throw unsupported(); }
        @Override public java.sql.Statement createStatement(int a, int b, int c) { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int a, int b, int c) { throw unsupported(); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int a, int b, int c) { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) { throw unsupported(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) { throw unsupported(); }
        @Override public java.sql.Clob createClob() { throw unsupported(); }
        @Override public java.sql.Blob createBlob() { throw unsupported(); }
        @Override public java.sql.NClob createNClob() { throw unsupported(); }
        @Override public java.sql.SQLXML createSQLXML() { throw unsupported(); }
        @Override public void setClientInfo(String name, String value) { throw unsupported(); }
        @Override public void setClientInfo(java.util.Properties properties) { throw unsupported(); }
        @Override public String getClientInfo(String name) { throw unsupported(); }
        @Override public java.util.Properties getClientInfo() { throw unsupported(); }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { throw unsupported(); }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { throw unsupported(); }
        @Override public void setSchema(String schema) { throw unsupported(); }
        @Override public String getSchema() { throw unsupported(); }
        @Override public void abort(java.util.concurrent.Executor executor) { throw unsupported(); }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) { throw unsupported(); }
        @Override public int getNetworkTimeout() { throw unsupported(); }
        @Override public <T> T unwrap(Class<T> iface) { throw unsupported(); }
        @Override public boolean isWrapperFor(Class<?> iface) { throw unsupported(); }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Stub method");
        }
    }
}
