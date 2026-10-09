ALTER TABLE reading_purchases ADD COLUMN counterpart_person_id UUID;
ALTER TABLE reading_purchases ADD COLUMN relation_type VARCHAR(30);
ALTER TABLE reading_purchases ADD COLUMN question_key VARCHAR(50);

ALTER TABLE readings ADD COLUMN subject_person_id UUID;
ALTER TABLE readings ADD COLUMN counterpart_person_id UUID;
ALTER TABLE readings ADD COLUMN counterpart_display_name VARCHAR(100);
ALTER TABLE readings ADD COLUMN relation_type VARCHAR(30);
ALTER TABLE readings ADD COLUMN question_key VARCHAR(50);

UPDATE readings
SET subject_person_id = (
    SELECT reading_purchases.subject_person_id
    FROM reading_purchases
    WHERE reading_purchases.id = readings.purchase_id
)
WHERE subject_person_id IS NULL;

ALTER TABLE readings ALTER COLUMN subject_person_id SET NOT NULL;
ALTER TABLE reading_purchases ADD CONSTRAINT reading_purchase_relation_type_ck
    CHECK (relation_type IS NULL OR relation_type IN ('FAMILY', 'FRIEND', 'LOVER', 'CRUSH', 'WORK_SCHOOL', 'OTHER'));
ALTER TABLE readings ADD CONSTRAINT reading_relation_type_ck
    CHECK (relation_type IS NULL OR relation_type IN ('FAMILY', 'FRIEND', 'LOVER', 'CRUSH', 'WORK_SCHOOL', 'OTHER'));
ALTER TABLE readings ADD CONSTRAINT readings_compatibility_context_ck CHECK (
    (fortune_type = 'COMPATIBILITY' AND counterpart_person_id IS NOT NULL
        AND counterpart_display_name IS NOT NULL AND relation_type IS NOT NULL AND question_key IS NOT NULL)
    OR (fortune_type <> 'COMPATIBILITY' AND counterpart_person_id IS NULL
        AND counterpart_display_name IS NULL AND relation_type IS NULL)
);

INSERT INTO products (id, code, category, price_currency, price_amount, active, catalog_version) VALUES
('b1000000-0000-0000-0000-000000000001', 'OVERALL_READING_ONLY', 'READING', 'TURTLE_SHELL', 10, TRUE, '2026-10-08'),
('b1000000-0000-0000-0000-000000000002', 'OVERALL_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, '2026-10-08'),
('b2000000-0000-0000-0000-000000000001', 'LOVE_READING_ONLY', 'READING', 'TURTLE_SHELL', 10, TRUE, '2026-10-08'),
('b2000000-0000-0000-0000-000000000002', 'LOVE_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, '2026-10-08'),
('b3000000-0000-0000-0000-000000000001', 'WEALTH_READING_ONLY', 'READING', 'TURTLE_SHELL', 10, TRUE, '2026-10-08'),
('b3000000-0000-0000-0000-000000000002', 'WEALTH_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, '2026-10-08'),
('b4000000-0000-0000-0000-000000000001', 'COMPATIBILITY_READING_ONLY', 'READING', 'TURTLE_SHELL', 10, TRUE, '2026-10-08'),
('b4000000-0000-0000-0000-000000000002', 'COMPATIBILITY_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, '2026-10-08'),
('b5000000-0000-0000-0000-000000000001', 'SINSAL_READING_ONLY', 'READING', 'TURTLE_SHELL', 10, TRUE, '2026-10-08'),
('b5000000-0000-0000-0000-000000000002', 'SINSAL_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, '2026-10-08');
