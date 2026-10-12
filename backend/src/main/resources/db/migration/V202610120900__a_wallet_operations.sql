CREATE TABLE wallet_integrity_findings (
    wallet_user_id UUID NOT NULL REFERENCES wallets(user_id),
    kind VARCHAR(40) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (wallet_user_id, kind)
);
