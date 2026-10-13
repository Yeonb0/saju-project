CREATE TABLE admin_gift_maintenance_requests (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    order_id UUID NOT NULL REFERENCES gift_order_drafts(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('PHONE_CLEANUP','PRIVATE_CLEANUP','PAYLOAD_REENCRYPTION')),
    key_hash VARCHAR(64) NOT NULL,
    reason_code VARCHAR(40) NOT NULL CHECK (reason_code IN ('SYSTEM_RECOVERY','CUSTOMER_INQUIRY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(operator_id,key_hash)
);
CREATE INDEX admin_gift_maintenance_order_idx ON admin_gift_maintenance_requests(order_id,kind);
