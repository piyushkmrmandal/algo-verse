-- =============================================================================
-- AlgoVerse :: Execution Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_execution.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE submission_status AS ENUM (
    'PENDING',
    'RUNNING',
    'ACCEPTED',
    'WRONG_ANSWER',
    'TIME_LIMIT_EXCEEDED',
    'MEMORY_LIMIT_EXCEEDED',
    'RUNTIME_ERROR',
    'COMPILATION_ERROR',
    'SYSTEM_ERROR'
);

CREATE TYPE test_result_status AS ENUM (
    'PASSED',
    'FAILED',
    'TLE',      -- Time Limit Exceeded
    'MLE',      -- Memory Limit Exceeded
    'RE',       -- Runtime Error
    'CE'        -- Compilation Error
);

-- ---------------------------------------------------------------------------
-- TABLE: submissions
-- One row per judge request. Immutable after judging completes.
-- ---------------------------------------------------------------------------
CREATE TABLE submissions (
    id                   UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID               NOT NULL,    -- references auth-service users.id
    problem_id           UUID               NOT NULL,    -- references problem-service problems.id
    language             VARCHAR(50)        NOT NULL,    -- e.g. 'python3', 'java21', 'cpp17'
    code                 TEXT               NOT NULL,
    status               submission_status  NOT NULL DEFAULT 'PENDING',
    runtime_ms           INTEGER            CHECK (runtime_ms >= 0),
    memory_mb            INTEGER            CHECK (memory_mb >= 0),
    test_cases_passed    INTEGER            NOT NULL DEFAULT 0 CHECK (test_cases_passed >= 0),
    test_cases_total     INTEGER            NOT NULL DEFAULT 0 CHECK (test_cases_total >= 0),
    error_message        TEXT,
    judge_output         JSONB,              -- raw judge response for debugging
    submitted_at         TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    judged_at            TIMESTAMPTZ,        -- NULL while PENDING or RUNNING

    CONSTRAINT submissions_test_cases_check
        CHECK (test_cases_passed <= test_cases_total)
);

COMMENT ON TABLE  submissions                IS 'Code submission records, one row per submission attempt.';
COMMENT ON COLUMN submissions.code           IS 'User-submitted source code; stored verbatim.';
COMMENT ON COLUMN submissions.judge_output   IS 'Raw JSONB blob from the sandbox judge for admin debugging.';
COMMENT ON COLUMN submissions.submitted_at   IS 'Time of receipt by execution service.';
COMMENT ON COLUMN submissions.judged_at      IS 'NULL until judging completes or terminates.';

-- ---------------------------------------------------------------------------
-- TABLE: submission_test_results
-- Per-test-case breakdown for a submission.
-- ---------------------------------------------------------------------------
CREATE TABLE submission_test_results (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID                NOT NULL,
    test_case_id    UUID                NOT NULL,       -- references problem-service test_cases.id
    status          test_result_status  NOT NULL,
    actual_output   TEXT,
    runtime_ms      INTEGER             CHECK (runtime_ms >= 0),
    memory_mb       INTEGER             CHECK (memory_mb >= 0),
    error_message   TEXT,

    CONSTRAINT fk_str_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  submission_test_results             IS 'Per-test-case execution result for each submission.';
COMMENT ON COLUMN submission_test_results.test_case_id IS 'Cross-service reference to problem-service.test_cases; no FK enforced across services.';

-- ---------------------------------------------------------------------------
-- TABLE: execution_quotas
-- Rate-limiting table: daily submission counts per user.
-- Upserted by the execution service before enqueuing a submission.
-- ---------------------------------------------------------------------------
CREATE TABLE execution_quotas (
    user_id             UUID          PRIMARY KEY,       -- references auth-service users.id
    daily_submissions   INTEGER       NOT NULL DEFAULT 0  CHECK (daily_submissions >= 0),
    daily_limit         INTEGER       NOT NULL DEFAULT 100 CHECK (daily_limit > 0),
    last_reset_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT execution_quotas_daily_check
        CHECK (daily_submissions <= daily_limit)
);

COMMENT ON TABLE  execution_quotas                IS 'Per-user daily submission quota tracking.';
COMMENT ON COLUMN execution_quotas.last_reset_at  IS 'Timestamp of the last midnight reset; used to determine if counter should be zeroed.';
COMMENT ON COLUMN execution_quotas.daily_limit    IS 'Configurable per-user limit; default 100 for FREE tier.';

-- ---------------------------------------------------------------------------
-- TABLE: sandbox_metrics
-- Low-level resource usage snapshots from the sandbox runtime.
-- ---------------------------------------------------------------------------
CREATE TABLE sandbox_metrics (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id        UUID          NOT NULL,
    cpu_usage_percent    DECIMAL(5,2)  CHECK (cpu_usage_percent BETWEEN 0 AND 100),
    wall_time_ms         INTEGER       CHECK (wall_time_ms >= 0),
    peak_memory_mb       INTEGER       CHECK (peak_memory_mb >= 0),
    syscall_count        INTEGER       CHECK (syscall_count >= 0),
    recorded_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_metrics_submission
        FOREIGN KEY (submission_id) REFERENCES submissions (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  sandbox_metrics             IS 'Fine-grained sandbox resource metrics, one row per submission (or per checkpoint).';
COMMENT ON COLUMN sandbox_metrics.syscall_count IS 'Total system call count; elevated values may indicate policy violations.';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- submissions
CREATE INDEX idx_submissions_user_submitted
    ON submissions (user_id, submitted_at DESC);

CREATE INDEX idx_submissions_problem_status
    ON submissions (problem_id, status);

CREATE INDEX idx_submissions_user_problem
    ON submissions (user_id, problem_id, submitted_at DESC);

CREATE INDEX idx_submissions_judged_at
    ON submissions (judged_at DESC)
    WHERE judged_at IS NOT NULL;

-- Partial index for the work queue: only PENDING and RUNNING rows need fast lookup
CREATE INDEX idx_submissions_pending_running
    ON submissions (submitted_at ASC)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX idx_submissions_language
    ON submissions (language, status);

-- submission_test_results
CREATE INDEX idx_str_submission_id
    ON submission_test_results (submission_id);

CREATE INDEX idx_str_test_case_id
    ON submission_test_results (test_case_id);

CREATE INDEX idx_str_status
    ON submission_test_results (status);

-- sandbox_metrics
CREATE INDEX idx_sandbox_metrics_submission_id
    ON sandbox_metrics (submission_id);

-- execution_quotas
CREATE INDEX idx_exec_quotas_last_reset
    ON execution_quotas (last_reset_at);

-- ---------------------------------------------------------------------------
-- TRIGGER: auto-update execution_quotas.updated_at
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

CREATE TRIGGER trg_execution_quotas_updated_at
    BEFORE UPDATE ON execution_quotas
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- FUNCTION: reset_daily_quota
-- Called by a scheduled job (pg_cron or application cron) each midnight UTC.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION reset_daily_quota()
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE execution_quotas
    SET
        daily_submissions = 0,
        last_reset_at     = NOW(),
        updated_at        = NOW()
    WHERE last_reset_at < DATE_TRUNC('day', NOW() AT TIME ZONE 'UTC');
END;
$$;

COMMENT ON FUNCTION reset_daily_quota IS
    'Resets daily_submissions to 0 for all users whose last reset was before today. '
    'Schedule via pg_cron: SELECT cron.schedule(''reset-quotas'', ''0 0 * * *'', ''SELECT reset_daily_quota()'');';
