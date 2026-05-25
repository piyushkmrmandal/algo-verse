-- =============================================================================
-- AlgoVerse :: Auth Service — Add provider / provider_id and normalize column names
-- Migration : V2__add_provider_fields.sql
-- =============================================================================

-- Add OAuth2 provider fields directly on the users table for simplified lookups
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider       VARCHAR(50)  NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS provider_id    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS mfa_enabled    BOOLEAN NOT NULL DEFAULT FALSE;

-- Migrate data from old column name if it exists (email_verified -> is_email_verified)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'email_verified'
    ) THEN
        UPDATE users SET is_email_verified = email_verified WHERE is_email_verified = FALSE;
    END IF;
END
$$;

-- Index for fast OAuth2 provider+providerId lookups
CREATE INDEX IF NOT EXISTS idx_users_provider_id
    ON users (provider, provider_id)
    WHERE provider != 'LOCAL';
