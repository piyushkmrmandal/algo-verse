-- =============================================================================
-- AlgoVerse :: Auth Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_auth.sql
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";    -- trigram similarity (future use)

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE user_role AS ENUM (
    'GUEST',
    'FREE',
    'PRO',
    'ENTERPRISE',
    'ADMIN'
);

-- ---------------------------------------------------------------------------
-- TABLE: users
-- Central identity record. Soft-deletable via deleted_at.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255)  NOT NULL,
    password_hash    VARCHAR(255),                        -- NULL for pure-OAuth accounts
    display_name     VARCHAR(100),
    avatar_url       TEXT,
    role             user_role     NOT NULL DEFAULT 'FREE',
    email_verified   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ,                         -- soft-delete sentinel

    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_format CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

COMMENT ON TABLE  users                IS 'Core user accounts for AlgoVerse.';
COMMENT ON COLUMN users.password_hash  IS 'bcrypt/argon2 hash; NULL for OAuth-only accounts.';
COMMENT ON COLUMN users.deleted_at     IS 'Non-NULL means soft-deleted; excluded from most queries via partial indexes.';

-- ---------------------------------------------------------------------------
-- TABLE: oauth_providers
-- Stores third-party OAuth tokens, encrypted at rest.
-- ---------------------------------------------------------------------------
CREATE TABLE oauth_providers (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID          NOT NULL,
    provider                VARCHAR(50)   NOT NULL,    -- e.g. 'github', 'google'
    provider_user_id        VARCHAR(255)  NOT NULL,
    access_token_encrypted  TEXT,
    refresh_token_encrypted TEXT,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_oauth_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT oauth_provider_unique
        UNIQUE (provider, provider_user_id)
);

COMMENT ON TABLE  oauth_providers                       IS 'OAuth 2.0 provider links per user.';
COMMENT ON COLUMN oauth_providers.access_token_encrypted  IS 'AES-256-GCM encrypted access token stored in app-layer vault.';
COMMENT ON COLUMN oauth_providers.refresh_token_encrypted IS 'AES-256-GCM encrypted refresh token stored in app-layer vault.';

-- ---------------------------------------------------------------------------
-- TABLE: sessions
-- Refresh-token session records. JWTs are stateless; only refresh tokens tracked.
-- ---------------------------------------------------------------------------
CREATE TABLE sessions (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID          NOT NULL,
    refresh_token_hash   VARCHAR(255)  UNIQUE,         -- SHA-256 of the actual token
    device_fingerprint   VARCHAR(255),
    ip_address           INET,
    user_agent           TEXT,
    expires_at           TIMESTAMPTZ   NOT NULL,
    revoked_at           TIMESTAMPTZ,                   -- non-NULL = revoked/logged-out
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_session_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  sessions                    IS 'Active and historical refresh-token sessions.';
COMMENT ON COLUMN sessions.refresh_token_hash IS 'SHA-256 hash of the opaque refresh token returned to client.';
COMMENT ON COLUMN sessions.revoked_at         IS 'Set on explicit logout, token rotation, or security revocation.';

-- ---------------------------------------------------------------------------
-- TABLE: mfa_configs
-- One MFA record per user (1:1).
-- ---------------------------------------------------------------------------
CREATE TABLE mfa_configs (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID          NOT NULL,
    totp_secret_encrypted  VARCHAR(255),              -- AES-encrypted TOTP seed
    backup_codes_encrypted TEXT[],                    -- encrypted one-time backup codes
    enabled                BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_mfa_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT mfa_configs_user_unique
        UNIQUE (user_id)
);

COMMENT ON TABLE  mfa_configs                        IS 'TOTP-based MFA configuration per user.';
COMMENT ON COLUMN mfa_configs.backup_codes_encrypted IS 'Array of individually AES-256-GCM encrypted 8-digit backup codes.';

-- ---------------------------------------------------------------------------
-- TABLE: audit_log
-- Immutable append-only event log. Never UPDATE or DELETE rows.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID,                               -- NULL for system/anonymous events
    action        VARCHAR(100)  NOT NULL,             -- e.g. 'LOGIN', 'PASSWORD_CHANGE'
    resource_type VARCHAR(100),                       -- e.g. 'user', 'session'
    resource_id   UUID,
    metadata      JSONB,                              -- flexible extra context
    ip_address    INET,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  audit_log          IS 'Immutable security and activity audit trail. Rows must never be updated or deleted.';
COMMENT ON COLUMN audit_log.metadata IS 'Free-form JSONB for event-specific data (e.g. old/new values, UA string).';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- users
CREATE INDEX idx_users_email         ON users (email)       WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role          ON users (role)        WHERE deleted_at IS NULL;
CREATE INDEX idx_users_created_at    ON users (created_at DESC);

-- oauth_providers
CREATE INDEX idx_oauth_user_id       ON oauth_providers (user_id);
CREATE INDEX idx_oauth_provider      ON oauth_providers (provider);

-- sessions
CREATE INDEX idx_sessions_user_id    ON sessions (user_id);
CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);

-- Partial index: only index sessions that have not been revoked (hot path for validation)
CREATE UNIQUE INDEX idx_sessions_active_token
    ON sessions (refresh_token_hash)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_sessions_active_user
    ON sessions (user_id)
    WHERE revoked_at IS NULL;

-- mfa_configs — FK already has unique index, add none extra

-- audit_log
CREATE INDEX idx_audit_user_id       ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_action        ON audit_log (action, created_at DESC);
CREATE INDEX idx_audit_resource      ON audit_log (resource_type, resource_id);

-- GIN index on audit_log.metadata for JSONB path queries
CREATE INDEX idx_audit_metadata_gin  ON audit_log USING GIN (metadata);

-- ---------------------------------------------------------------------------
-- TRIGGER: auto-update updated_at on users
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_set_updated_at IS 'Generic trigger function that sets updated_at = NOW() before any row update.';

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- ROW LEVEL SECURITY (RLS)
-- ---------------------------------------------------------------------------

-- Enable RLS on users table
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Policy: authenticated users may only SELECT their own (non-deleted) row.
-- The application must set app.current_user_id via SET LOCAL before querying.
CREATE POLICY users_select_own
    ON users
    FOR SELECT
    USING (
        id = current_setting('app.current_user_id', TRUE)::UUID
        AND deleted_at IS NULL
    );

-- Policy: service-role bypass (used by trusted backend service accounts).
-- Grant BYPASSRLS to the service DB role; this policy is a safety net for app roles.
CREATE POLICY users_service_all
    ON users
    FOR ALL
    TO CURRENT_USER              -- placeholder; replace with your service DB role
    USING (TRUE)
    WITH CHECK (TRUE);

COMMENT ON TABLE users IS
    'RLS enabled: application must SET LOCAL app.current_user_id = <uuid> '
    'before issuing queries under an unprivileged role. '
    'Service accounts should hold the BYPASSRLS privilege.';
