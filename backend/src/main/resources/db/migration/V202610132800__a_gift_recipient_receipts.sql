CREATE TABLE gift_recipient_receipts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES gift_purchase_work(order_id) ON DELETE CASCADE,
    request_hash VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    review_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state VARCHAR(30) NOT NULL CHECK (state IN ('ACCEPTED','PROVIDED','REVIEW_REQUIRED')),
    content_id UUID,
    recipient_user_id UUID,
    linked_at TIMESTAMP WITH TIME ZONE,
    CHECK ((recipient_user_id IS NULL) = (linked_at IS NULL)),
    CHECK (state <> 'PROVIDED' OR content_id IS NOT NULL)
);
CREATE INDEX gift_recipient_review_idx ON gift_recipient_receipts(state,review_at,order_id);
ALTER TABLE gift_order_drafts ADD COLUMN private_cleanup_failures INTEGER NOT NULL DEFAULT 0 CHECK (private_cleanup_failures BETWEEN 0 AND 5);
ALTER TABLE gift_order_drafts ADD COLUMN private_cleanup_next_attempt_at TIMESTAMP WITH TIME ZONE;
