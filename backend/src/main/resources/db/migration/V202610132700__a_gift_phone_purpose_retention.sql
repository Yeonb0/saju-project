ALTER TABLE gift_order_drafts ADD COLUMN phone_purged_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE gift_order_drafts ADD COLUMN phone_cleanup_failures INTEGER NOT NULL DEFAULT 0 CHECK (phone_cleanup_failures BETWEEN 0 AND 5);
ALTER TABLE gift_order_drafts ADD COLUMN phone_cleanup_next_attempt_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX gift_draft_phone_cleanup_idx ON gift_order_drafts(phone_purged_at,created_at,id);
