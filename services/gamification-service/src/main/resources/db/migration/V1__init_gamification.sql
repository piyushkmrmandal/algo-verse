-- =============================================================================
-- AlgoVerse :: Gamification Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_gamification.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE xp_source AS ENUM (
    'SUBMISSION_ACCEPTED',
    'FIRST_SOLVE',
    'HARD_SOLVE',
    'DAILY_CHALLENGE',
    'STREAK_BONUS',
    'CONTEST_PLACEMENT',
    'REVIEW_UPVOTED',
    'COLLAB_SESSION'
);

CREATE TYPE badge_rarity AS ENUM (
    'COMMON',
    'RARE',
    'EPIC',
    'LEGENDARY'
);

CREATE TYPE leaderboard_period AS ENUM (
    'DAILY',
    'WEEKLY',
    'MONTHLY',
    'ALL_TIME'
);

-- ---------------------------------------------------------------------------
-- TABLE: user_xp
-- Aggregate XP totals per user. Single source of truth for level/rank.
-- ---------------------------------------------------------------------------
CREATE TABLE user_xp (
    user_id      UUID          PRIMARY KEY,    -- references auth-service users.id
    total_xp     INTEGER       NOT NULL DEFAULT 0 CHECK (total_xp >= 0),
    level        INTEGER       NOT NULL DEFAULT 1 CHECK (level >= 1),
    weekly_xp    INTEGER       NOT NULL DEFAULT 0 CHECK (weekly_xp >= 0),
    monthly_xp   INTEGER       NOT NULL DEFAULT 0 CHECK (monthly_xp >= 0),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  user_xp            IS 'Aggregated XP totals and computed level for each user.';
COMMENT ON COLUMN user_xp.total_xp   IS 'Lifetime XP; never decremented.';
COMMENT ON COLUMN user_xp.weekly_xp  IS 'XP earned in the current ISO calendar week; reset by scheduled job.';
COMMENT ON COLUMN user_xp.monthly_xp IS 'XP earned in the current calendar month; reset by scheduled job.';

-- ---------------------------------------------------------------------------
-- TABLE: xp_transactions
-- Append-only ledger of every XP award event.
-- ---------------------------------------------------------------------------
CREATE TABLE xp_transactions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,           -- references auth-service users.id
    amount        INTEGER     NOT NULL,            -- can be negative for corrections
    source        xp_source   NOT NULL,
    reference_id  UUID,                            -- submission_id, badge_id, etc.
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  xp_transactions              IS 'Immutable XP ledger; sum(amount) WHERE user_id = X must equal user_xp.total_xp.';
COMMENT ON COLUMN xp_transactions.reference_id IS 'Cross-service entity that triggered this XP award.';

-- ---------------------------------------------------------------------------
-- TABLE: streaks
-- Daily activity streak tracking per user.
-- ---------------------------------------------------------------------------
CREATE TABLE streaks (
    user_id             UUID          PRIMARY KEY,   -- references auth-service users.id
    current_streak      INTEGER       NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    longest_streak      INTEGER       NOT NULL DEFAULT 0 CHECK (longest_streak >= 0),
    last_activity_date  DATE,
    freeze_count        INTEGER       NOT NULL DEFAULT 2 CHECK (freeze_count >= 0),
    next_reset_at       TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT streaks_longest_gte_current
        CHECK (longest_streak >= current_streak)
);

COMMENT ON TABLE  streaks                  IS 'Daily coding streak state per user.';
COMMENT ON COLUMN streaks.freeze_count     IS 'Remaining streak-freeze tokens (default 2 per month).';
COMMENT ON COLUMN streaks.next_reset_at    IS 'Precomputed deadline by which the user must be active to preserve streak.';

-- ---------------------------------------------------------------------------
-- TABLE: badges
-- Badge catalogue (predefined by platform).
-- ---------------------------------------------------------------------------
CREATE TABLE badges (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             VARCHAR(100)  NOT NULL,
    name             VARCHAR(100)  NOT NULL,
    description      TEXT,
    icon_url         TEXT,
    rarity           badge_rarity  NOT NULL DEFAULT 'COMMON',
    xp_reward        INTEGER       NOT NULL DEFAULT 0 CHECK (xp_reward >= 0),
    condition_type   VARCHAR(100)  NOT NULL,         -- e.g. 'PROBLEMS_SOLVED', 'STREAK_DAYS'
    condition_value  JSONB         NOT NULL,          -- e.g. {"threshold": 100}

    CONSTRAINT badges_slug_unique UNIQUE (slug)
);

COMMENT ON TABLE  badges                  IS 'Platform badge definitions.';
COMMENT ON COLUMN badges.condition_type   IS 'Opaque string matched by badge-award engine: PROBLEMS_SOLVED, STREAK_DAYS, HARD_SOLVE_COUNT, etc.';
COMMENT ON COLUMN badges.condition_value  IS 'JSONB parameters for the condition evaluator, e.g. {"threshold": 100, "difficulty": "HARD"}.';

-- ---------------------------------------------------------------------------
-- TABLE: user_badges
-- Awarded badge instances per user.
-- ---------------------------------------------------------------------------
CREATE TABLE user_badges (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL,    -- references auth-service users.id
    badge_id    UUID          NOT NULL,
    earned_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_badge_badge
        FOREIGN KEY (badge_id) REFERENCES badges (id)
        ON DELETE CASCADE,

    CONSTRAINT user_badges_unique
        UNIQUE (user_id, badge_id)
);

COMMENT ON TABLE  user_badges IS 'Records of badges awarded to users; UNIQUE constraint prevents duplicate awards.';

-- ---------------------------------------------------------------------------
-- TABLE: leaderboard_snapshots
-- Materialized point-in-time leaderboard positions.
-- Written by a background aggregation job, never updated in place.
-- ---------------------------------------------------------------------------
CREATE TABLE leaderboard_snapshots (
    id               UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    period           leaderboard_period   NOT NULL,
    snapshot_date    DATE                 NOT NULL,
    user_id          UUID                 NOT NULL,   -- references auth-service users.id
    rank             INTEGER              NOT NULL CHECK (rank >= 1),
    xp               INTEGER              NOT NULL CHECK (xp >= 0),
    problems_solved  INTEGER              NOT NULL DEFAULT 0 CHECK (problems_solved >= 0),
    created_at       TIMESTAMPTZ          NOT NULL DEFAULT NOW(),

    CONSTRAINT leaderboard_snapshots_unique
        UNIQUE (period, snapshot_date, user_id)
);

COMMENT ON TABLE  leaderboard_snapshots              IS 'Immutable leaderboard snapshots; one row per user per period per date.';
COMMENT ON COLUMN leaderboard_snapshots.snapshot_date IS 'For DAILY: the date. For WEEKLY: the ISO week Monday. For MONTHLY: the 1st of the month.';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- user_xp
CREATE INDEX idx_user_xp_total
    ON user_xp (total_xp DESC);

CREATE INDEX idx_user_xp_weekly
    ON user_xp (weekly_xp DESC);

CREATE INDEX idx_user_xp_monthly
    ON user_xp (monthly_xp DESC);

-- xp_transactions
CREATE INDEX idx_xp_tx_user_created
    ON xp_transactions (user_id, created_at DESC);

CREATE INDEX idx_xp_tx_source
    ON xp_transactions (source, created_at DESC);

CREATE INDEX idx_xp_tx_reference_id
    ON xp_transactions (reference_id)
    WHERE reference_id IS NOT NULL;

-- streaks
CREATE INDEX idx_streaks_current
    ON streaks (current_streak DESC);

-- badges
CREATE INDEX idx_badges_rarity
    ON badges (rarity);

-- user_badges
CREATE INDEX idx_user_badges_user_id
    ON user_badges (user_id, earned_at DESC);

CREATE INDEX idx_user_badges_badge_id
    ON user_badges (badge_id);

-- leaderboard_snapshots
CREATE INDEX idx_leaderboard_period_date_rank
    ON leaderboard_snapshots (period, snapshot_date DESC, rank ASC);

CREATE INDEX idx_leaderboard_user_id
    ON leaderboard_snapshots (user_id, period, snapshot_date DESC);

-- ---------------------------------------------------------------------------
-- TRIGGER: auto-update updated_at on user_xp and streaks
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

CREATE TRIGGER trg_user_xp_updated_at
    BEFORE UPDATE ON user_xp
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_streaks_updated_at
    BEFORE UPDATE ON streaks
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- TRIGGER: award XP and update user_xp on new xp_transaction
-- Keeps the aggregate table in sync automatically.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_apply_xp_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO user_xp (user_id, total_xp, weekly_xp, monthly_xp, updated_at)
    VALUES (NEW.user_id, NEW.amount, NEW.amount, NEW.amount, NOW())
    ON CONFLICT (user_id) DO UPDATE
        SET total_xp     = user_xp.total_xp  + EXCLUDED.total_xp,
            weekly_xp    = user_xp.weekly_xp  + EXCLUDED.weekly_xp,
            monthly_xp   = user_xp.monthly_xp + EXCLUDED.monthly_xp,
            updated_at   = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_xp_transaction_apply
    AFTER INSERT ON xp_transactions
    FOR EACH ROW
    EXECUTE FUNCTION fn_apply_xp_transaction();

COMMENT ON FUNCTION fn_apply_xp_transaction IS
    'Upserts user_xp totals whenever an xp_transaction row is inserted. '
    'Ensures aggregate table is always consistent with the ledger.';

-- ---------------------------------------------------------------------------
-- FUNCTION: compute_level
-- Simple XP → level formula: level = FLOOR(1 + SQRT(total_xp / 100))
-- Replace the formula to match game-design requirements.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION compute_level(p_total_xp INTEGER)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE STRICT
AS $$
    SELECT GREATEST(1, FLOOR(1 + SQRT(p_total_xp::NUMERIC / 100)))::INTEGER;
$$;

COMMENT ON FUNCTION compute_level IS
    'Converts total_xp to level using: level = max(1, floor(1 + sqrt(xp/100))). '
    'Used by the application layer after each XP award to recalculate user_xp.level.';

-- ---------------------------------------------------------------------------
-- FUNCTION: reset_periodic_xp
-- Called by pg_cron: zeroes weekly_xp on Mondays and monthly_xp on 1st of month.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION reset_periodic_xp(p_period TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_period = 'weekly' THEN
        UPDATE user_xp SET weekly_xp = 0, updated_at = NOW();
    ELSIF p_period = 'monthly' THEN
        UPDATE user_xp SET monthly_xp = 0, updated_at = NOW();
    ELSE
        RAISE EXCEPTION 'Unknown period: %', p_period;
    END IF;
END;
$$;

COMMENT ON FUNCTION reset_periodic_xp IS
    'Resets periodic XP counters. '
    'Schedule: weekly → every Monday 00:01 UTC; monthly → every 1st 00:01 UTC.';
