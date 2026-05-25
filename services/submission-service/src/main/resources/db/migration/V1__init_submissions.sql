-- AlgoVerse Submission Service — initial schema
-- V1__init_submissions.sql

CREATE TABLE IF NOT EXISTS submissions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    problem_slug       VARCHAR(255) NOT NULL,
    language           VARCHAR(20)  NOT NULL,
    code               TEXT         NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    difficulty         VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    execution_time_ms  INTEGER,
    memory_used_kb     INTEGER,
    passed_test_cases  INTEGER      DEFAULT 0,
    total_test_cases   INTEGER      DEFAULT 0,
    error_message      TEXT,
    is_first_solve     BOOLEAN      DEFAULT false,
    submitted_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    graded_at          TIMESTAMPTZ
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_submissions_user_id      ON submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_problem_slug ON submissions(problem_slug);
CREATE INDEX IF NOT EXISTS idx_submissions_status       ON submissions(status);
CREATE INDEX IF NOT EXISTS idx_submissions_user_problem ON submissions(user_id, problem_slug);
CREATE INDEX IF NOT EXISTS idx_submissions_submitted_at ON submissions(submitted_at DESC);
