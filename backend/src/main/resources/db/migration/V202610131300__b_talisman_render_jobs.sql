CREATE TABLE talisman_render_jobs (
    talisman_id UUID PRIMARY KEY REFERENCES talismans(id),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'IN_FLIGHT', 'COMPLETED', 'EXHAUSTED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    fence BIGINT NOT NULL DEFAULT 0 CHECK (fence >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_failure_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (
        (state = 'PENDING' AND lease_token IS NULL AND lease_until IS NULL)
        OR (state = 'IN_FLIGHT' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state IN ('COMPLETED', 'EXHAUSTED') AND lease_token IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX talisman_render_jobs_poll_idx
    ON talisman_render_jobs(state, next_attempt_at, talisman_id);
