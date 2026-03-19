-- Add risk scoring + decisioning fields to loan_application_draft
-- These fields store the latest automated evaluation output for MVP instant decisioning.

ALTER TABLE loan_application_draft
    ADD COLUMN IF NOT EXISTS risk_score INTEGER,
    ADD COLUMN IF NOT EXISTS decision VARCHAR(32),
    ADD COLUMN IF NOT EXISTS decision_reason TEXT,
    ADD COLUMN IF NOT EXISTS decisioned_at TIMESTAMPTZ;
