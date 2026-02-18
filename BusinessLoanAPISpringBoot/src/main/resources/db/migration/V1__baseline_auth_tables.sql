-- Baseline schema for auth-related tables used by the current codebase.
-- This is intended for PostgreSQL (Neon).
--
-- Important: Hibernate is configured with ddl-auto=validate, so these DDL statements
-- are the source of truth for schema.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token TEXT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Helpful index for cleanup and user lookup patterns.
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id ON email_verification_tokens(user_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(48) NOT NULL,
    email TEXT,
    source_ip TEXT NOT NULL,
    -- JPA field name is "timestamp"; since it's a reserved-ish identifier, keep it quoted.
    "timestamp" TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    meta TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_log_email ON audit_log(email);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON audit_log("timestamp");
