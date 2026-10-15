-- Direct gift checkout is distinct from historical wallet-funded gift drafts.
CREATE TABLE direct_gift_batches (
    payment_batch_id UUID PRIMARY KEY REFERENCES direct_payment_batches(id),
    buyer_user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PAYMENT_PENDING','PAID','FULFILLING','FULFILLED','REVIEW_REQUIRED')),
    recipient_count INTEGER NOT NULL CHECK (recipient_count > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fulfilled_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE direct_gift_batch_recipients (
    id UUID PRIMARY KEY,
    payment_batch_id UUID NOT NULL REFERENCES direct_gift_batches(payment_batch_id) ON DELETE CASCADE,
    recipient_index INTEGER NOT NULL CHECK (recipient_index >= 0),
    option VARCHAR(30) NOT NULL CHECK (option IN ('TALISMAN_ONLY','READING_WITH_TALISMAN')),
    amount_krw INTEGER NOT NULL CHECK (amount_krw IN (1000,3000)),
    token_hash CHAR(64),
    state VARCHAR(30) NOT NULL CHECK (state IN ('PENDING_PAYMENT','LINK_ACTIVE','PROCESSING','READY','FAILED','REVIEW_REQUIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(payment_batch_id, recipient_index),
    CHECK ((option='TALISMAN_ONLY' AND amount_krw=1000) OR (option='READING_WITH_TALISMAN' AND amount_krw=3000))
);

CREATE INDEX direct_gift_recipients_token_idx ON direct_gift_batch_recipients(token_hash) WHERE token_hash IS NOT NULL;
