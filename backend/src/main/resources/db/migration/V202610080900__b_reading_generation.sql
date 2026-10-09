CREATE TABLE reading_results (
    id UUID PRIMARY KEY,
    generation_key CHAR(64) NOT NULL UNIQUE,
    fortune_type VARCHAR(30) NOT NULL,
    reference_date DATE NOT NULL,
    facts_json TEXT NOT NULL,
    sections_json TEXT,
    calculation_version VARCHAR(100) NOT NULL,
    generation_version VARCHAR(100) NOT NULL,
    content_version VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATING', 'SUCCEEDED', 'FAILED')),
    failure_code VARCHAR(100),
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE generation_attempts (
    id UUID PRIMARY KEY,
    reading_result_id UUID NOT NULL REFERENCES reading_results(id),
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    provider VARCHAR(100) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    error_code VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (reading_result_id, attempt_no)
);

CREATE INDEX reading_results_status_lease_idx ON reading_results(status, lease_until);
