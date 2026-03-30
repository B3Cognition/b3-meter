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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple JDBC connection pool using {@link ConcurrentHashMap} and {@link ArrayBlockingQueue}.
 *
 * <p>Connections are pooled per URL key. When a connection is requested, the pool
 * first tries to poll from an existing queue. If no idle connection is available
 * (or the queue does not yet exist), a new connection is created via
 * {@link DriverManager#getConnection}.
 *
 * <p>Only JDK types are used — no external pooling library (Constitution Principle I).
 */
public final class JDBCConnectionPool {

    private static final Logger LOG = Logger.getLogger(JDBCConnectionPool.class.getName());

    /** Singleton instance. */
    private static final JDBCConnectionPool INSTANCE = new JDBCConnectionPool();

    /** Pool keyed by JDBC URL. Each queue holds idle connections. */
    private final ConcurrentHashMap<String, ArrayBlockingQueue<Connection>> pools =
            new ConcurrentHashMap<>();

    private JDBCConnectionPool() {
        // singleton
    }

    /**
     * Returns the singleton pool instance.
     *
     * @return the shared connection pool
     */
    public static JDBCConnectionPool getInstance() {
        return INSTANCE;
    }

    /**
     * Borrows a connection from the pool, or creates a new one if none are idle.
     *
     * @param url      JDBC connection URL; must not be {@code null}
     * @param user     database username; may be {@code null}
     * @param password database password; may be {@code null}
     * @param poolSize maximum pool size (used to size the queue on first access)
     * @return a valid JDBC connection
     * @throws SQLException if connection creation fails
     */
    public Connection getConnection(String url, String user, String password, int poolSize)
            throws SQLException {
        ArrayBlockingQueue<Connection> queue = pools.computeIfAbsent(url,
                k -> new ArrayBlockingQueue<>(Math.max(poolSize, 1)));

        // Try to reuse an idle connection
        Connection conn = queue.poll();
        if (conn != null && isValid(conn)) {
            return conn;
        }

        // Close stale connection if it was invalid
        if (conn != null) {
            closeQuietly(conn);
        }

        // Create a new connection
        LOG.log(Level.FINE, "JDBCConnectionPool: creating new connection to {0}", url);
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Returns a connection to the pool for reuse. If the pool queue is full
     * or the connection is invalid, the connection is closed instead.
     *
     * @param url  the JDBC URL key; must not be {@code null}
     * @param conn the connection to return; must not be {@code null}
     */
    public void returnConnection(String url, Connection conn) {
        if (conn == null) return;

        if (!isValid(conn)) {
            closeQuietly(conn);
            return;
        }

        ArrayBlockingQueue<Connection> queue = pools.get(url);
        if (queue == null || !queue.offer(conn)) {
            // Pool full or not found — close the connection
            closeQuietly(conn);
        }
    }

    /**
     * Drains and closes all pooled connections across all URLs.
     */
    public void closeAll() {
        for (Map.Entry<String, ArrayBlockingQueue<Connection>> entry : pools.entrySet()) {
            ArrayBlockingQueue<Connection> queue = entry.getValue();
            Connection conn;
            while ((conn = queue.poll()) != null) {
                closeQuietly(conn);
            }
        }
        pools.clear();
        LOG.log(Level.INFO, "JDBCConnectionPool: all connections closed");
    }

    /**
     * Checks whether a connection is still valid (not closed, responds within 1 second).
     *
     * @param conn the connection to check
     * @return {@code true} if the connection is valid
     */
    public boolean isValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Closes a connection, suppressing any exception.
     */
    private static void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "JDBCConnectionPool: error closing connection", e);
        }
    }
}
