ALTER TABLE reading_purchases ADD COLUMN failure_code VARCHAR(80);
ALTER TABLE reading_purchases ADD COLUMN compensation_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE reading_purchases ADD CONSTRAINT reading_purchase_compensation_status_check
    CHECK (compensation_status IN ('NOT_REQUIRED', 'PENDING', 'COMPLETED', 'REVIEW_REQUIRED'));
ALTER TABLE reading_purchases ADD CONSTRAINT reading_purchase_failure_code_check
    CHECK (status = 'FAILED' OR failure_code IS NULL);
