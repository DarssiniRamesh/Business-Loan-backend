-- Supporting document metadata for secure uploads (PDF/JPG/PNG)
--
-- Storage strategy:
-- - Metadata in Postgres.
-- - File bytes stored on the application filesystem under a configurable root directory.
-- - This schema stores a storage key that maps to the physical file path (relative to root).
--
-- Ownership model:
-- - Stored per authenticated user (JWT subject = app_user.id UUID).
-- - Optionally linked to a loan_application_draft.

CREATE TABLE IF NOT EXISTS supporting_document (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    loan_draft_id UUID NULL REFERENCES loan_application_draft(id) ON DELETE SET NULL,

    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,

    -- SHA-256 of the file content (hex string), useful for integrity / dedup checks.
    sha256_hex CHAR(64) NOT NULL,

    -- Storage locator (relative key/path within the configured storage root).
    storage_key VARCHAR(512) NOT NULL,

    -- Free-form metadata (e.g., "docType":"BANK_STATEMENT","period":"2024-12")
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_supporting_document_user_id ON supporting_document(user_id);
CREATE INDEX IF NOT EXISTS idx_supporting_document_loan_draft_id ON supporting_document(loan_draft_id);
CREATE INDEX IF NOT EXISTS idx_supporting_document_created_at ON supporting_document(created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_supporting_document_storage_key ON supporting_document(storage_key);
