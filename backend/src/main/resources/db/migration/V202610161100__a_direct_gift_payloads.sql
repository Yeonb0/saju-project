-- Direct-payment gifts need the same encrypted display payload as legacy gift drafts.
-- Existing recipients remain readable as legacy records with no recoverable display payload.
ALTER TABLE direct_gift_batches ADD COLUMN request_hash CHAR(64);
ALTER TABLE direct_gift_batch_recipients ADD COLUMN private_payload TEXT;
