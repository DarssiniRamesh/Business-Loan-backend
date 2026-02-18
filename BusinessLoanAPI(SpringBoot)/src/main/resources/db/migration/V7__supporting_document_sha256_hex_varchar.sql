-- Fix schema validation mismatch for supporting_document.sha256_hex.
--
-- Previous migration (V4) created sha256_hex as CHAR(64) which is reported by Postgres as bpchar (Types#CHAR).
-- Hibernate validates the entity mapping as VARCHAR, causing application startup to fail under ddl-auto=validate.
--
-- This migration changes the column type to VARCHAR(64) while preserving existing data.

ALTER TABLE supporting_document
    ALTER COLUMN sha256_hex TYPE VARCHAR(64)
    USING BTRIM(sha256_hex);
