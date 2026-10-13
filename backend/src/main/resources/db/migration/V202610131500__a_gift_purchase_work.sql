CREATE TABLE gift_purchase_work (
    order_id UUID PRIMARY KEY REFERENCES gift_order_drafts(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REFUNDED', 'FAILED', 'REVIEW_REQUIRED')),
    failures INTEGER NOT NULL DEFAULT 0 CHECK (failures BETWEEN 0 AND 5),
    failure_code VARCHAR(80),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    wallet_transaction_id UUID UNIQUE,
    charged_amount INTEGER CHECK (charged_amount > 0),
    approved_at TIMESTAMP WITH TIME ZONE,
    gift_id UUID UNIQUE,
    access_expires_at TIMESTAMP WITH TIME ZONE,
    CHECK (status <> 'ACTIVE' OR (wallet_transaction_id IS NOT NULL AND charged_amount IS NOT NULL
        AND approved_at IS NOT NULL AND gift_id IS NOT NULL AND access_expires_at > approved_at))
);
CREATE INDEX idx_gift_purchase_due ON gift_purchase_work(status, next_attempt_at, order_id);

CREATE TABLE gift_purchase_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES gift_purchase_work(order_id),
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE gift_delivery_outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES gift_purchase_work(order_id),
    private_payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
