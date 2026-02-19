-- Adds submission locking metadata to loan application drafts.
-- After a draft is submitted, it must become immutable (no update/patch/delete).

ALTER TABLE loan_application_draft
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_loan_application_draft_submitted_at
    ON loan_application_draft(submitted_at);
