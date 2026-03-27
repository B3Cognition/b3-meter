package com.jmeternext.web.api.repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * JDBC implementation of {@link SampleResultRepository} backed by H2.
 *
 * <p>{@link #insertBatch} uses {@link JdbcTemplate#batchUpdate} for efficient
 * bulk inserts — each call sends a single JDBC batch rather than N round trips.
 */
@Repository
public class JdbcSampleResultRepository implements SampleResultRepository {

    private static final String INSERT_BUCKET = """
            INSERT INTO sample_results
                (run_id, timestamp, sampler_label, sample_count, error_count,
                 avg_response_time, min_response_time, max_response_time,
                 percentile_90, percentile_95, percentile_99, samples_per_second)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_RUN_TIME_RANGE = """
            SELECT run_id, timestamp, sampler_label, sample_count, error_count,
                   avg_response_time, min_response_time, max_response_time,
                   percentile_90, percentile_95, percentile_99, samples_per_second
            FROM sample_results
            WHERE run_id = ? AND timestamp >= ? AND timestamp < ?
            ORDER BY timestamp ASC
            """;

    private final JdbcTemplate jdbc;

    public JdbcSampleResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertBatch(String runId, List<SampleBucketRow> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_BUCKET, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SampleBucketRow row = buckets.get(i);
                ps.setString(1, runId);
                ps.setTimestamp(2, Timestamp.from(row.timestamp()));
                ps.setString(3, row.samplerLabel());
                ps.setLong(4, row.sampleCount());
                ps.setLong(5, row.errorCount());
                ps.setDouble(6, row.avgResponseTime());
                ps.setDouble(7, row.minResponseTime());
                ps.setDouble(8, row.maxResponseTime());
                ps.setDouble(9, row.percentile90());
                ps.setDouble(10, row.percentile95());
                ps.setDouble(11, row.percentile99());
                ps.setDouble(12, row.samplesPerSecond());
            }

            @Override
            public int getBatchSize() {
                return buckets.size();
            }
        });
    }

    @Override
    public List<SampleBucketRow> findByRunId(String runId, Instant from, Instant to) {
        return jdbc.query(
                SELECT_BY_RUN_TIME_RANGE,
                BUCKET_ROW_MAPPER,
                runId,
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    // -------------------------------------------------------------------------
    // Row mapper
    // -------------------------------------------------------------------------

    private static final RowMapper<SampleBucketRow> BUCKET_ROW_MAPPER = new RowMapper<>() {
        @Override
        public SampleBucketRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SampleBucketRow(
                    rs.getString("run_id"),
                    rs.getTimestamp("timestamp").toInstant(),
                    rs.getString("sampler_label"),
                    rs.getLong("sample_count"),
                    rs.getLong("error_count"),
                    rs.getDouble("avg_response_time"),
                    rs.getDouble("min_response_time"),
                    rs.getDouble("max_response_time"),
                    rs.getDouble("percentile_90"),
                    rs.getDouble("percentile_95"),
                    rs.getDouble("percentile_99"),
                    rs.getDouble("samples_per_second")
            );
        }
    };
}
