-- Nullable for upgrade compatibility with any pre-existing reading rows. New bundled
-- Suneung fulfillment writes both values atomically; a later backfill can tighten this.
ALTER TABLE readings ADD COLUMN talisman_id UUID;
ALTER TABLE readings ADD COLUMN talisman_status VARCHAR(20)
    CHECK (talisman_status IN ('PENDING', 'READY'));
CREATE UNIQUE INDEX readings_talisman_id_uq ON readings(talisman_id);
