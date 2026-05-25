-- ============================================================
-- AlgoVerse Analytics Service — Initial Schema
-- V1__init_analytics.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS platform_metrics (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_date  DATE         NOT NULL,
    metric_key   VARCHAR(100) NOT NULL,
    metric_value BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_platform_metrics_date_key UNIQUE (metric_date, metric_key)
);

CREATE TABLE IF NOT EXISTS problem_stats (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_slug   VARCHAR(255) NOT NULL UNIQUE,
    difficulty     VARCHAR(10)  NOT NULL,
    total_attempts BIGINT       NOT NULL DEFAULT 0,
    total_accepted BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_platform_metrics_date       ON platform_metrics(metric_date);
CREATE INDEX IF NOT EXISTS idx_platform_metrics_key        ON platform_metrics(metric_key);
CREATE INDEX IF NOT EXISTS idx_problem_stats_slug          ON problem_stats(problem_slug);
CREATE INDEX IF NOT EXISTS idx_problem_stats_difficulty    ON problem_stats(difficulty);
CREATE INDEX IF NOT EXISTS idx_problem_stats_attempts_desc ON problem_stats(total_attempts DESC);
