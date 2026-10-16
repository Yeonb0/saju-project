-- A direct-payment recipient must become a normal gift work item before a
-- bearer link is exposed.  This keeps recipient encryption, leases and result
-- snapshots on the existing durable gift pipeline instead of a second worker.
ALTER TABLE gift_order_drafts ALTER COLUMN quote_id DROP NOT NULL;

ALTER TABLE gift_order_drafts DROP CONSTRAINT gift_order_drafts_shell_amount_check;
ALTER TABLE gift_order_drafts ADD CONSTRAINT gift_order_drafts_shell_amount_check CHECK (shell_amount >= 0);

ALTER TABLE gift_purchase_work ADD COLUMN direct_payment_batch_id UUID
    REFERENCES direct_payment_batches(id);

ALTER TABLE gift_purchase_work DROP CONSTRAINT gift_purchase_work_check;
ALTER TABLE gift_purchase_work ADD CONSTRAINT gift_purchase_work_active_settlement_check CHECK (
    status <> 'ACTIVE' OR (
        approved_at IS NOT NULL AND gift_id IS NOT NULL AND access_expires_at > approved_at
        AND ((wallet_transaction_id IS NOT NULL AND charged_amount IS NOT NULL)
             OR direct_payment_batch_id IS NOT NULL)
    )
);

ALTER TABLE direct_gift_batch_recipients ADD COLUMN order_id UUID UNIQUE
    REFERENCES gift_order_drafts(id);
ALTER TABLE direct_gift_batch_recipients ADD COLUMN gift_id UUID UNIQUE
    REFERENCES gift_purchase_work(gift_id);

CREATE INDEX direct_gift_recipients_gift_idx ON direct_gift_batch_recipients(gift_id)
    WHERE gift_id IS NOT NULL;
