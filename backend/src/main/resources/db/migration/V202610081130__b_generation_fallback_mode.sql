ALTER TABLE reading_results
    ADD COLUMN generation_mode VARCHAR(20) NOT NULL DEFAULT 'LINER'
    CHECK (generation_mode IN ('LINER', 'FALLBACK'));

ALTER TABLE readings
    ADD COLUMN generation_mode VARCHAR(20) NOT NULL DEFAULT 'LINER'
    CHECK (generation_mode IN ('LINER', 'FALLBACK'));
