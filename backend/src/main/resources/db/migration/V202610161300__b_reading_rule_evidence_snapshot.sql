-- Nullable columns preserve read compatibility for existing immutable snapshots during rollout.
ALTER TABLE readings ADD COLUMN rule_version VARCHAR(100);
ALTER TABLE readings ADD COLUMN evidence_summary TEXT;
