-- =============================================================================
-- AlgoVerse :: Auth Service — Align schema with JPA entity definitions
-- Migration : V3__align_schema.sql
-- =============================================================================

-- Convert role from custom ENUM to VARCHAR for JPA @Enumerated(STRING) compatibility
ALTER TABLE users
    ALTER COLUMN role TYPE VARCHAR(20) USING role::VARCHAR;

-- Rename email_verified to is_email_verified if the old column still exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'email_verified'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'is_email_verified'
    ) THEN
        ALTER TABLE users RENAME COLUMN email_verified TO is_email_verified;
    END IF;
END
$$;

-- Ensure display_name has a NOT NULL default for new rows
ALTER TABLE users
    ALTER COLUMN display_name SET DEFAULT '';

-- Set NOT NULL constraints where the entity requires them (safe since V2 added defaults)
ALTER TABLE users
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN is_email_verified SET NOT NULL,
    ALTER COLUMN mfa_enabled SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL;

-- Drop the pg_enum type if no other tables use it
-- (done last to avoid FK issues)
DROP TYPE IF EXISTS user_role CASCADE;
