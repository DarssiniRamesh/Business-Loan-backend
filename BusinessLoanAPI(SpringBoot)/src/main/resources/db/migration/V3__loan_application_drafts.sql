-- Loan application drafts for multi-step wizard persistence
--
-- Notes:
-- - Stored per authenticated user (by JWT subject = app_user.id UUID).
-- - Uses JSONB for flexible draft payload storage.
-- - `section_status` tracks per-section completion state for wizard UX.
-- - `version` is used for optimistic concurrency control at the API layer.

CREATE TABLE IF NOT EXISTS loan_application_draft (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,

    -- Overall draft data (merged representation)
    data JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- Per-section status map, e.g. {"businessInfo":"IN_PROGRESS","ownerInfo":"COMPLETED"}
    section_status JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- Wizard state helpers
    current_step VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    -- Optimistic concurrency (incremented on each write)
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_loan_application_draft_user_id ON loan_application_draft(user_id);
CREATE INDEX IF NOT EXISTS idx_loan_application_draft_updated_at ON loan_application_draft(updated_at);
