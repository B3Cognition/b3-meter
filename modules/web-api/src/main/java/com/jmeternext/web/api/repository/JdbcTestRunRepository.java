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
 * JDBC implementation of {@link TestRunRepository} backed by H2.
 */
@Repository
public class JdbcTestRunRepository implements TestRunRepository {

    private static final String INSERT_RUN = """
            INSERT INTO test_runs (id, plan_id, status, started_at, ended_at, total_samples, error_count, owner_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, plan_id, status, started_at, ended_at, total_samples, error_count, owner_id
            FROM test_runs
            WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, plan_id, status, started_at, ended_at, total_samples, error_count, owner_id
            FROM test_runs
            ORDER BY id
            """;

    private static final String SELECT_BY_PLAN = """
            SELECT id, plan_id, status, started_at, ended_at, total_samples, error_count, owner_id
            FROM test_runs
            WHERE plan_id = ?
            ORDER BY id
            """;

    private static final String UPDATE_STATUS = """
            UPDATE test_runs SET status = ? WHERE id = ?
            """;

    private static final String COUNT_ACTIVE = """
            SELECT COUNT(*) FROM test_runs WHERE status IN ('RUNNING', 'STOPPING')
            """;

    private static final String UPDATE_COMPLETION = """
            UPDATE test_runs SET status = ?, total_samples = ?, error_count = ?, ended_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcTestRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TestRunEntity create(TestRunEntity run) {
        jdbc.update(INSERT_RUN,
                run.id(),
                run.planId(),
                run.status(),
                run.startedAt() != null ? Timestamp.from(run.startedAt()) : null,
                run.endedAt() != null ? Timestamp.from(run.endedAt()) : null,
                run.totalSamples(),
                run.errorCount(),
                run.ownerId()
        );
        return run;
    }

    @Override
    public Optional<TestRunEntity> findById(String id) {
        List<TestRunEntity> results = jdbc.query(SELECT_BY_ID, RUN_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<TestRunEntity> findAll() {
        return jdbc.query(SELECT_ALL, RUN_ROW_MAPPER);
    }

    @Override
    public List<TestRunEntity> findByPlanId(String planId) {
        return jdbc.query(SELECT_BY_PLAN, RUN_ROW_MAPPER, planId);
    }

    @Override
    public int countActive() {
        Integer count = jdbc.queryForObject(COUNT_ACTIVE, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public void updateStatus(String id, String status) {
        jdbc.update(UPDATE_STATUS, status, id);
    }

    @Override
    public void updateCompletion(String id, String status, long totalSamples, long errorCount) {
        jdbc.update(UPDATE_COMPLETION, status, totalSamples, errorCount, id);
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private static final RowMapper<TestRunEntity> RUN_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TestRunEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TestRunEntity(
                    rs.getString("id"),
                    rs.getString("plan_id"),
                    rs.getString("status"),
                    toInstant(rs.getTimestamp("started_at")),
                    toInstant(rs.getTimestamp("ended_at")),
                    rs.getLong("total_samples"),
                    rs.getLong("error_count"),
                    rs.getString("owner_id")
            );
        }
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
