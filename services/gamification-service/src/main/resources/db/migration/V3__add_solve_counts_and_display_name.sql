-- =============================================================================
-- AlgoVerse :: Gamification Service — Add solve counts and display_name to user_xp
-- Migration : V3__add_solve_counts_and_display_name.sql
-- =============================================================================

-- Add display_name column for leaderboard rendering
ALTER TABLE user_xp
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150);

-- Add per-difficulty solve counters
ALTER TABLE user_xp
    ADD COLUMN IF NOT EXISTS easy_solves   INTEGER NOT NULL DEFAULT 0 CHECK (easy_solves >= 0);

ALTER TABLE user_xp
    ADD COLUMN IF NOT EXISTS medium_solves INTEGER NOT NULL DEFAULT 0 CHECK (medium_solves >= 0);

ALTER TABLE user_xp
    ADD COLUMN IF NOT EXISTS hard_solves   INTEGER NOT NULL DEFAULT 0 CHECK (hard_solves >= 0);

ALTER TABLE user_xp
    ADD COLUMN IF NOT EXISTS total_solves  INTEGER NOT NULL DEFAULT 0 CHECK (total_solves >= 0);

-- Index for display_name leaderboard lookups
CREATE INDEX IF NOT EXISTS idx_user_xp_display_name
    ON user_xp (display_name);

COMMENT ON COLUMN user_xp.display_name  IS 'Cached display name for leaderboard rendering; synced from auth-service.';
COMMENT ON COLUMN user_xp.easy_solves   IS 'Count of distinct EASY problems solved (first-solve only).';
COMMENT ON COLUMN user_xp.medium_solves IS 'Count of distinct MEDIUM problems solved (first-solve only).';
COMMENT ON COLUMN user_xp.hard_solves   IS 'Count of distinct HARD problems solved (first-solve only).';
COMMENT ON COLUMN user_xp.total_solves  IS 'Total distinct problems solved across all difficulties.';
