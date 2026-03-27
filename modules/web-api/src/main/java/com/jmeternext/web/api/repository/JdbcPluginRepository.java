package com.jmeternext.web.api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link PluginRepository} backed by H2 / SQLite.
 */
@Repository
public class JdbcPluginRepository implements PluginRepository {

    private static final String INSERT_PLUGIN = """
            INSERT INTO plugins (id, name, version, jar_path, status, installed_by, installed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, name, version, jar_path, status, installed_by, installed_at
            FROM plugins
            WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, name, version, jar_path, status, installed_by, installed_at
            FROM plugins
            ORDER BY installed_at ASC
            """;

    private static final String UPDATE_STATUS = """
            UPDATE plugins SET status = ? WHERE id = ?
            """;

    private static final String DELETE_BY_ID = """
            DELETE FROM plugins WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcPluginRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PluginEntity create(PluginEntity plugin) {
        jdbc.update(INSERT_PLUGIN,
                plugin.id(),
                plugin.name(),
                plugin.version(),
                plugin.jarPath(),
                plugin.status(),
                plugin.installedBy(),
                plugin.installedAt() != null ? Timestamp.from(plugin.installedAt()) : null
        );
        return plugin;
    }

    @Override
    public Optional<PluginEntity> findById(String id) {
        List<PluginEntity> results = jdbc.query(SELECT_BY_ID, PLUGIN_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<PluginEntity> findAll() {
        return jdbc.query(SELECT_ALL, PLUGIN_ROW_MAPPER);
    }

    @Override
    public void updateStatus(String id, String status) {
        jdbc.update(UPDATE_STATUS, status, id);
    }

    @Override
    public boolean delete(String id) {
        int rows = jdbc.update(DELETE_BY_ID, id);
        return rows > 0;
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private static final RowMapper<PluginEntity> PLUGIN_ROW_MAPPER = new RowMapper<>() {
        @Override
        public PluginEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PluginEntity(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("version"),
                    rs.getString("jar_path"),
                    rs.getString("status"),
                    rs.getString("installed_by"),
                    toInstant(rs.getTimestamp("installed_at"))
            );
        }
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
