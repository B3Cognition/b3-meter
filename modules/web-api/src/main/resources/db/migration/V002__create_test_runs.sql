CREATE TABLE test_runs (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL REFERENCES test_plans(id),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    total_samples BIGINT DEFAULT 0,
    error_count BIGINT DEFAULT 0,
    owner_id VARCHAR(36)
);

CREATE TABLE sample_results (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL REFERENCES test_runs(id),
    timestamp TIMESTAMP NOT NULL,
    sampler_label VARCHAR(255) NOT NULL,
    sample_count BIGINT NOT NULL,
    error_count BIGINT NOT NULL,
    avg_response_time DOUBLE PRECISION NOT NULL,
    min_response_time DOUBLE PRECISION NOT NULL,
    max_response_time DOUBLE PRECISION NOT NULL,
    percentile_90 DOUBLE PRECISION,
    percentile_95 DOUBLE PRECISION,
    percentile_99 DOUBLE PRECISION,
    samples_per_second DOUBLE PRECISION
);
CREATE INDEX idx_sample_results_run_ts ON sample_results(run_id, timestamp);
