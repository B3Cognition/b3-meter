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
 * JDBC implementation of {@link TestPlanRepository} backed by H2 and PostgreSQL.
 *
 * <p>Upsert is implemented via a portable INSERT-or-UPDATE pattern: first attempt an
 * UPDATE; if no rows were affected (the plan does not exist yet), perform an INSERT.
 * This works identically on H2, PostgreSQL, and any ANSI-SQL database.
 */
@Repository
public class JdbcTestPlanRepository implements TestPlanRepository {

    private static final String UPDATE_PLAN = """
            UPDATE test_plans
            SET name = ?, owner_id = ?, tree_data = ?, updated_at = ?, deleted_at = ?
            WHERE id = ?
            """;

    private static final String INSERT_PLAN = """
            INSERT INTO test_plans (id, name, owner_id, tree_data, created_at, updated_at, deleted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, name, owner_id, tree_data, created_at, updated_at, deleted_at
            FROM test_plans
            WHERE id = ? AND deleted_at IS NULL
            """;

    private static final String SELECT_BY_OWNER = """
            SELECT id, name, owner_id, tree_data, created_at, updated_at, deleted_at
            FROM test_plans
            WHERE owner_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

    private static final String SELECT_ALL = """
            SELECT id, name, owner_id, tree_data, created_at, updated_at, deleted_at
            FROM test_plans
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC
            """;

    private static final String SOFT_DELETE = """
            UPDATE test_plans SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?
            """;

    private static final String INSERT_REVISION = """
            INSERT INTO test_plan_revisions (id, plan_id, revision_number, tree_data, created_at, author)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_REVISIONS = """
            SELECT id, plan_id, revision_number, tree_data, created_at, author
            FROM test_plan_revisions
            WHERE plan_id = ?
            ORDER BY revision_number DESC
            """;

    private final JdbcTemplate jdbc;

    public JdbcTestPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TestPlanEntity save(TestPlanEntity plan) {
        Instant now = plan.createdAt() != null ? plan.createdAt() : Instant.now();
        Instant updatedAt = plan.updatedAt() != null ? plan.updatedAt() : Instant.now();
        Timestamp deletedTs = plan.deletedAt() != null ? Timestamp.from(plan.deletedAt()) : null;

        // Portable upsert: try UPDATE first; if no rows matched, INSERT.
        int updated = jdbc.update(UPDATE_PLAN,
                plan.name(),
                plan.ownerId(),
                plan.treeData(),
                Timestamp.from(updatedAt),
                deletedTs,
                plan.id()
        );
        if (updated == 0) {
            jdbc.update(INSERT_PLAN,
                    plan.id(),
                    plan.name(),
                    plan.ownerId(),
                    plan.treeData(),
                    Timestamp.from(now),
                    Timestamp.from(updatedAt),
                    deletedTs
            );
        }
        return plan;
    }

    @Override
    public Optional<TestPlanEntity> findById(String id) {
        List<TestPlanEntity> results = jdbc.query(SELECT_BY_ID, PLAN_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<TestPlanEntity> findByOwnerId(String ownerId) {
        return jdbc.query(SELECT_BY_OWNER, PLAN_ROW_MAPPER, ownerId);
    }

    @Override
    public List<TestPlanEntity> findAll() {
        return jdbc.query(SELECT_ALL, PLAN_ROW_MAPPER);
    }

    @Override
    public void deleteById(String id) {
        jdbc.update(SOFT_DELETE, id);
    }

    @Override
    public void saveRevision(TestPlanRevisionEntity revision) {
        Instant createdAt = revision.createdAt() != null ? revision.createdAt() : Instant.now();
        jdbc.update(INSERT_REVISION,
                revision.id(),
                revision.planId(),
                revision.revisionNumber(),
                revision.treeData(),
                Timestamp.from(createdAt),
                revision.author()
        );
    }

    @Override
    public List<TestPlanRevisionEntity> findRevisions(String planId) {
        return jdbc.query(SELECT_REVISIONS, REVISION_ROW_MAPPER, planId);
    }

    // -------------------------------------------------------------------------
    // Row mappers
    // -------------------------------------------------------------------------

    private static final RowMapper<TestPlanEntity> PLAN_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TestPlanEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TestPlanEntity(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("owner_id"),
                    rs.getString("tree_data"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    toInstant(rs.getTimestamp("deleted_at"))
            );
        }
    };

    private static final RowMapper<TestPlanRevisionEntity> REVISION_ROW_MAPPER = new RowMapper<>() {
        @Override
        public TestPlanRevisionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TestPlanRevisionEntity(
                    rs.getString("id"),
                    rs.getString("plan_id"),
                    rs.getInt("revision_number"),
                    rs.getString("tree_data"),
                    toInstant(rs.getTimestamp("created_at")),
                    rs.getString("author")
            );
        }
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
