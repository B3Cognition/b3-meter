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
 * JDBC implementation of {@link WorkerRepository} backed by H2.
 */
@Repository
public class JdbcWorkerRepository implements WorkerRepository {

    private static final String INSERT_WORKER = """
            INSERT INTO workers (id, hostname, port, status, last_heartbeat, registered_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, hostname, port, status, last_heartbeat, registered_at
            FROM workers
            WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, hostname, port, status, last_heartbeat, registered_at
            FROM workers
            ORDER BY registered_at
            """;

    private static final String DELETE_BY_ID = """
            DELETE FROM workers WHERE id = ?
            """;

    private static final String UPDATE_STATUS = """
            UPDATE workers SET status = ? WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcWorkerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WorkerEntity save(WorkerEntity worker) {
        jdbc.update(INSERT_WORKER,
                worker.id(),
                worker.hostname(),
                worker.port(),
                worker.status(),
                worker.lastHeartbeat() != null ? Timestamp.from(worker.lastHeartbeat()) : null,
                worker.registeredAt() != null ? Timestamp.from(worker.registeredAt()) : null
        );
        return worker;
    }

    @Override
    public Optional<WorkerEntity> findById(String id) {
        List<WorkerEntity> results = jdbc.query(SELECT_BY_ID, WORKER_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<WorkerEntity> findAll() {
        return jdbc.query(SELECT_ALL, WORKER_ROW_MAPPER);
    }

    @Override
    public boolean deleteById(String id) {
        int rows = jdbc.update(DELETE_BY_ID, id);
        return rows > 0;
    }

    @Override
    public void updateStatus(String id, String status) {
        jdbc.update(UPDATE_STATUS, status, id);
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private static final RowMapper<WorkerEntity> WORKER_ROW_MAPPER = new RowMapper<>() {
        @Override
        public WorkerEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new WorkerEntity(
                    rs.getString("id"),
                    rs.getString("hostname"),
                    rs.getInt("port"),
                    rs.getString("status"),
                    toInstant(rs.getTimestamp("last_heartbeat")),
                    toInstant(rs.getTimestamp("registered_at"))
            );
        }
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
