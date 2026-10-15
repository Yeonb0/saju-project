CREATE TABLE gift_result_snapshots (
    id UUID PRIMARY KEY,
    gift_id UUID NOT NULL UNIQUE REFERENCES gift_purchase_work(gift_id) ON DELETE CASCADE,
    receipt_id UUID NOT NULL UNIQUE REFERENCES gift_recipient_receipts(id) ON DELETE CASCADE,
    content_id UUID NOT NULL UNIQUE,
    product_code VARCHAR(100) NOT NULL,
    content_version VARCHAR(40) NOT NULL,
    generation_version VARCHAR(40) NOT NULL,
    result_json TEXT NOT NULL,
    result_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX gift_result_snapshots_receipt_idx ON gift_result_snapshots(receipt_id);
