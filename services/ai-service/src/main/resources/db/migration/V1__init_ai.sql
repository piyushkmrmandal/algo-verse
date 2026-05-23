-- =============================================================================
-- AlgoVerse :: AI Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_ai.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ---------------------------------------------------------------------------
-- TABLE: skill_models
-- Bayesian Knowledge Tracing (BKT) parameters per (user, skill) pair.
-- Used to estimate the learner's latent knowledge state.
-- ---------------------------------------------------------------------------
CREATE TABLE skill_models (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL,        -- references auth-service users.id
    skill_id       VARCHAR(100)  NOT NULL,         -- matches topics.slug from problem-service
    p_know         DECIMAL(6,5)  NOT NULL DEFAULT 0.10000
                                 CHECK (p_know   BETWEEN 0 AND 1),
    p_learn        DECIMAL(6,5)  NOT NULL DEFAULT 0.30000
                                 CHECK (p_learn  BETWEEN 0 AND 1),
    p_guess        DECIMAL(6,5)  NOT NULL DEFAULT 0.20000
                                 CHECK (p_guess  BETWEEN 0 AND 1),
    p_slip         DECIMAL(6,5)  NOT NULL DEFAULT 0.10000
                                 CHECK (p_slip   BETWEEN 0 AND 1),
    attempt_count  INTEGER       NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_updated   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT skill_models_unique
        UNIQUE (user_id, skill_id)
);

COMMENT ON TABLE  skill_models          IS 'Per-(user,skill) Bayesian Knowledge Tracing parameters.';
COMMENT ON COLUMN skill_models.p_know   IS 'P(Know): probability the student currently knows the skill.';
COMMENT ON COLUMN skill_models.p_learn  IS 'P(Learn): probability of learning the skill on each attempt.';
COMMENT ON COLUMN skill_models.p_guess  IS 'P(Guess): probability of correct answer despite not knowing.';
COMMENT ON COLUMN skill_models.p_slip   IS 'P(Slip): probability of incorrect answer despite knowing.';

-- ---------------------------------------------------------------------------
-- TABLE: hint_requests
-- Logs every AI-generated hint, including cost metadata.
-- ---------------------------------------------------------------------------
CREATE TABLE hint_requests (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL,   -- references auth-service users.id
    problem_id          UUID          NOT NULL,   -- references problem-service problems.id
    submission_id       UUID,                     -- optional: specific submission context
    hint_level          INTEGER       NOT NULL
                        CHECK (hint_level BETWEEN 1 AND 5),
    hint_text           TEXT          NOT NULL,
    model_used          VARCHAR(100),             -- e.g. 'claude-sonnet-4-6'
    prompt_tokens       INTEGER       CHECK (prompt_tokens >= 0),
    completion_tokens   INTEGER       CHECK (completion_tokens >= 0),
    latency_ms          INTEGER       CHECK (latency_ms >= 0),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  hint_requests              IS 'Log of AI-generated hints per user/problem interaction.';
COMMENT ON COLUMN hint_requests.hint_level   IS 'Scaffolding level 1 (vague nudge) to 5 (near-solution).';
COMMENT ON COLUMN hint_requests.model_used   IS 'Exact model version used for reproducibility and cost tracking.';
COMMENT ON COLUMN hint_requests.prompt_tokens IS 'Input token count for cost attribution.';

-- ---------------------------------------------------------------------------
-- TABLE: code_reviews
-- AI-generated code review for each submission (one per submission).
-- ---------------------------------------------------------------------------
CREATE TABLE code_reviews (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID          NOT NULL,   -- references auth-service users.id
    submission_id        UUID          NOT NULL,   -- references execution-service submissions.id
    review_text          TEXT          NOT NULL,   -- human-readable narrative review
    issues               JSONB,                    -- [{severity, line, message, category}]
    suggestions          JSONB,                    -- [{title, description, code_snippet}]
    complexity_analysis  JSONB,                    -- {time: "O(n log n)", space: "O(n)", explanation}
    pattern_detected     VARCHAR(100),             -- e.g. 'two_pointer', 'dynamic_programming'
    quality_score        DECIMAL(4,2)  CHECK (quality_score BETWEEN 0 AND 10),
    model_used           VARCHAR(100),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT code_reviews_submission_unique
        UNIQUE (submission_id)
);

COMMENT ON TABLE  code_reviews                    IS 'AI-generated code quality reviews, one per submission.';
COMMENT ON COLUMN code_reviews.issues             IS 'Array of issue objects: [{severity: "ERROR|WARNING|INFO", line: int, message: str, category: str}].';
COMMENT ON COLUMN code_reviews.suggestions        IS 'Array of improvement suggestions with optional code snippets.';
COMMENT ON COLUMN code_reviews.complexity_analysis IS 'Big-O analysis returned by the AI, stored as JSONB for structured display.';
COMMENT ON COLUMN code_reviews.pattern_detected   IS 'Primary algorithmic pattern identified, matching topics.slug.';

-- ---------------------------------------------------------------------------
-- TABLE: learning_paths
-- Personalised, AI-generated learning plans per user.
-- ---------------------------------------------------------------------------
CREATE TABLE learning_paths (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL,    -- references auth-service users.id
    name              VARCHAR(200)  NOT NULL,
    goal              VARCHAR(100),              -- e.g. 'FAANG_INTERVIEW', 'COMPETITIVE_PROGRAMMING'
    topics            JSONB         NOT NULL DEFAULT '[]',
                                                 -- [{topic_id, topic_name, weight}]
    problem_sequence  JSONB         NOT NULL DEFAULT '[]',
                                                 -- ordered [{problem_id, problem_slug, rationale}]
    estimated_hours   INTEGER       CHECK (estimated_hours > 0),
    current_position  INTEGER       NOT NULL DEFAULT 0 CHECK (current_position >= 0),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  learning_paths                   IS 'AI-generated personalised learning paths per user.';
COMMENT ON COLUMN learning_paths.topics            IS 'JSONB array of topic weights driving problem selection.';
COMMENT ON COLUMN learning_paths.problem_sequence  IS 'Ordered problem list generated by the ZPD-based recommendation engine.';
COMMENT ON COLUMN learning_paths.current_position  IS '0-based index into problem_sequence for resume-from-last-position.';

-- ---------------------------------------------------------------------------
-- TABLE: ai_conversations
-- Multi-turn chat threads with the AI tutor, per user/problem context.
-- ---------------------------------------------------------------------------
CREATE TABLE ai_conversations (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL,    -- references auth-service users.id
    problem_id  UUID,                      -- NULL = general tutoring, non-NULL = problem-specific
    thread_id   VARCHAR(255),              -- external thread ID if using stateful API
    messages    JSONB         NOT NULL DEFAULT '[]',
                                           -- [{role, content, created_at, tokens}]
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_conversations          IS 'Multi-turn AI tutor conversation threads.';
COMMENT ON COLUMN ai_conversations.messages IS 'JSONB array of {role: "user"|"assistant", content: str, created_at: iso8601, tokens: int}.';
COMMENT ON COLUMN ai_conversations.thread_id IS 'Persistent thread ID for stateful model APIs (e.g. Anthropic Threads).';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- skill_models
CREATE INDEX idx_skill_models_user_id
    ON skill_models (user_id);

-- ZPD query: find skills a user is ready to learn (p_know in the learning zone)
-- Zone of Proximal Development: p_know typically 0.4–0.7
CREATE INDEX idx_skill_models_user_zpd
    ON skill_models (user_id, p_know DESC)
    WHERE p_know BETWEEN 0.2 AND 0.8;

CREATE INDEX idx_skill_models_skill_id
    ON skill_models (skill_id);

-- hint_requests
CREATE INDEX idx_hint_requests_user_problem
    ON hint_requests (user_id, problem_id, created_at DESC);

CREATE INDEX idx_hint_requests_user_id
    ON hint_requests (user_id, created_at DESC);

CREATE INDEX idx_hint_requests_problem_id
    ON hint_requests (problem_id, created_at DESC);

CREATE INDEX idx_hint_requests_submission_id
    ON hint_requests (submission_id)
    WHERE submission_id IS NOT NULL;

-- code_reviews
CREATE INDEX idx_code_reviews_user_id
    ON code_reviews (user_id, created_at DESC);

CREATE INDEX idx_code_reviews_pattern
    ON code_reviews (pattern_detected)
    WHERE pattern_detected IS NOT NULL;

-- learning_paths
CREATE INDEX idx_learning_paths_user_id
    ON learning_paths (user_id, created_at DESC);

-- ai_conversations
CREATE INDEX idx_ai_conversations_user_id
    ON ai_conversations (user_id, updated_at DESC);

CREATE INDEX idx_ai_conversations_user_problem
    ON ai_conversations (user_id, problem_id)
    WHERE problem_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- TRIGGERS: auto-update updated_at
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

CREATE TRIGGER trg_learning_paths_updated_at
    BEFORE UPDATE ON learning_paths
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_ai_conversations_updated_at
    BEFORE UPDATE ON ai_conversations
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_skill_models_updated_at
    BEFORE UPDATE ON skill_models
    FOR EACH ROW
    -- Reuse fn_set_updated_at but map to last_updated column name via alias
    EXECUTE FUNCTION fn_set_updated_at();

-- Note: skill_models uses last_updated (not updated_at); the trigger above works
-- because fn_set_updated_at references NEW.updated_at. For skill_models we use
-- a dedicated function:
DROP TRIGGER trg_skill_models_updated_at ON skill_models;

CREATE OR REPLACE FUNCTION fn_set_skill_last_updated()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.last_updated = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_skill_models_last_updated
    BEFORE UPDATE ON skill_models
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_skill_last_updated();

-- ---------------------------------------------------------------------------
-- FUNCTION: bkt_update
-- Applies a single BKT observation (correct/incorrect) to a skill_model row.
-- Returns the updated p_know value.
--
-- BKT update equations:
--   P(Know_n | correct)   = [P(Know_n) * (1 - p_slip)]
--                           / [P(Know_n) * (1 - p_slip) + (1 - P(Know_n)) * p_guess]
--   P(Know_n | incorrect) = [P(Know_n) * p_slip]
--                           / [P(Know_n) * p_slip + (1 - P(Know_n)) * (1 - p_guess)]
--   P(Know_n+1)           = P(Know_n | obs) + (1 - P(Know_n | obs)) * p_learn
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION bkt_update(
    p_user_id   UUID,
    p_skill_id  VARCHAR(100),
    p_correct   BOOLEAN
)
RETURNS DECIMAL(6,5)
LANGUAGE plpgsql
AS $$
DECLARE
    v_row          skill_models%ROWTYPE;
    v_p_know_given DECIMAL(6,5);
    v_p_know_next  DECIMAL(6,5);
BEGIN
    SELECT * INTO v_row
    FROM skill_models
    WHERE user_id = p_user_id AND skill_id = p_skill_id
    FOR UPDATE;

    IF NOT FOUND THEN
        INSERT INTO skill_models (user_id, skill_id)
        VALUES (p_user_id, p_skill_id)
        RETURNING * INTO v_row;
    END IF;

    -- Posterior: P(Know | observation)
    IF p_correct THEN
        v_p_know_given := (v_row.p_know * (1 - v_row.p_slip))
                        / (v_row.p_know * (1 - v_row.p_slip)
                           + (1 - v_row.p_know) * v_row.p_guess);
    ELSE
        v_p_know_given := (v_row.p_know * v_row.p_slip)
                        / (v_row.p_know * v_row.p_slip
                           + (1 - v_row.p_know) * (1 - v_row.p_guess));
    END IF;

    -- Prediction for next step: account for learning transition
    v_p_know_next := v_p_know_given + (1 - v_p_know_given) * v_row.p_learn;

    -- Clamp to [0, 1]
    v_p_know_next := LEAST(1.0, GREATEST(0.0, v_p_know_next));

    UPDATE skill_models
    SET
        p_know        = v_p_know_next,
        attempt_count = attempt_count + 1,
        last_updated  = NOW()
    WHERE user_id = p_user_id AND skill_id = p_skill_id;

    RETURN v_p_know_next;
END;
$$;

COMMENT ON FUNCTION bkt_update IS
    'Performs a single Bayesian Knowledge Tracing update for (user, skill). '
    'Returns the new p_know estimate. Call after each problem attempt.';
