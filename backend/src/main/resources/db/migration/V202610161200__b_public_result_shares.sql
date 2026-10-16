CREATE TABLE public_result_shares (
    id UUID PRIMARY KEY,
    public_token_hash CHAR(64) NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL,
    source_kind VARCHAR(16) NOT NULL CHECK (source_kind IN ('READING', 'TALISMAN')),
    source_id UUID NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (source_kind, source_id)
);
CREATE INDEX public_result_shares_token_active_idx ON public_result_shares(public_token_hash, expires_at)
    WHERE revoked_at IS NULL;
