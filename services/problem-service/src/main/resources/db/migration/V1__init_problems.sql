-- =============================================================================
-- AlgoVerse :: Problem Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_problems.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- supports GIN trigram FTS

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE problem_difficulty AS ENUM (
    'EASY',
    'MEDIUM',
    'HARD'
);

CREATE TYPE problem_status AS ENUM (
    'DRAFT',
    'PUBLISHED',
    'DEPRECATED'
);

CREATE TYPE user_problem_status AS ENUM (
    'NOT_STARTED',
    'ATTEMPTED',
    'SOLVED'
);

-- ---------------------------------------------------------------------------
-- TABLE: topics
-- Self-referential tree (e.g. Algorithms > Sorting > Merge Sort).
-- ---------------------------------------------------------------------------
CREATE TABLE topics (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(100)  NOT NULL,
    parent_id   UUID,                                 -- NULL = root topic
    description TEXT,
    icon        VARCHAR(50),                          -- e.g. Lucide icon name

    CONSTRAINT topics_name_unique UNIQUE (name),
    CONSTRAINT topics_slug_unique UNIQUE (slug),
    CONSTRAINT fk_topics_parent
        FOREIGN KEY (parent_id) REFERENCES topics (id)
        ON DELETE SET NULL
);

COMMENT ON TABLE  topics           IS 'DSA topic taxonomy (tree structure).';
COMMENT ON COLUMN topics.parent_id IS 'NULL for root-level topics; non-NULL for sub-topics.';

-- ---------------------------------------------------------------------------
-- TABLE: problems
-- ---------------------------------------------------------------------------
CREATE TABLE problems (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             VARCHAR(200)    NOT NULL,
    title            VARCHAR(500)    NOT NULL,
    description      TEXT            NOT NULL,
    difficulty       problem_difficulty NOT NULL,
    status           problem_status  NOT NULL DEFAULT 'DRAFT',
    acceptance_rate  DECIMAL(5,2)    CHECK (acceptance_rate BETWEEN 0 AND 100),
    submission_count INTEGER         NOT NULL DEFAULT 0,
    tags             TEXT[]          NOT NULL DEFAULT '{}',
    companies        TEXT[]          NOT NULL DEFAULT '{}',
    constraints      TEXT,
    hints            TEXT[]          NOT NULL DEFAULT '{}',
    editorial_id     UUID,                             -- FK added after editorials table
    created_by       UUID,                             -- references auth-service users.id
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Full-text search computed column
    search_vector    TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(description, '')), 'B')
        ) STORED,

    CONSTRAINT problems_slug_unique UNIQUE (slug),
    CONSTRAINT problems_acceptance_rate_check CHECK (acceptance_rate IS NULL OR acceptance_rate BETWEEN 0.00 AND 100.00)
);

COMMENT ON TABLE  problems               IS 'DSA problem catalogue.';
COMMENT ON COLUMN problems.search_vector IS 'Auto-maintained tsvector for full-text search; title weighted A, description weighted B.';
COMMENT ON COLUMN problems.editorial_id  IS 'Denormalized forward reference to editorials.id; also has FK added below.';

-- ---------------------------------------------------------------------------
-- TABLE: test_cases
-- ---------------------------------------------------------------------------
CREATE TABLE test_cases (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id       UUID          NOT NULL,
    input            TEXT          NOT NULL,
    expected_output  TEXT          NOT NULL,
    is_sample        BOOLEAN       NOT NULL DEFAULT FALSE,  -- shown in problem statement
    is_hidden        BOOLEAN       NOT NULL DEFAULT FALSE,  -- hidden from user
    weight           DECIMAL(4,2)  NOT NULL DEFAULT 1.0,
    time_limit_ms    INTEGER       NOT NULL DEFAULT 2000    CHECK (time_limit_ms > 0),
    memory_limit_mb  INTEGER       NOT NULL DEFAULT 256     CHECK (memory_limit_mb > 0),
    explanation      TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_test_case_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  test_cases           IS 'Input/output test cases for each problem.';
COMMENT ON COLUMN test_cases.is_hidden IS 'Hidden test cases are used for judging but never revealed to users.';
COMMENT ON COLUMN test_cases.weight    IS 'Scoring weight when partial grading is enabled.';

-- ---------------------------------------------------------------------------
-- TABLE: problem_templates
-- Language-specific starter and solution code.
-- ---------------------------------------------------------------------------
CREATE TABLE problem_templates (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id     UUID         NOT NULL,
    language       VARCHAR(50)  NOT NULL,
    starter_code   TEXT         NOT NULL,
    solution_code  TEXT         NOT NULL,

    CONSTRAINT fk_template_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE,

    CONSTRAINT problem_templates_unique
        UNIQUE (problem_id, language)
);

COMMENT ON TABLE  problem_templates             IS 'Per-language starter and reference solution code.';
COMMENT ON COLUMN problem_templates.language    IS 'Language identifier, e.g. ''python3'', ''java'', ''cpp17''.';
COMMENT ON COLUMN problem_templates.solution_code IS 'Authoritative solution — never exposed to end-users via API.';

-- ---------------------------------------------------------------------------
-- TABLE: problem_topics  (many-to-many join)
-- ---------------------------------------------------------------------------
CREATE TABLE problem_topics (
    problem_id  UUID  NOT NULL,
    topic_id    UUID  NOT NULL,

    PRIMARY KEY (problem_id, topic_id),

    CONSTRAINT fk_pt_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_pt_topic
        FOREIGN KEY (topic_id) REFERENCES topics (id)
        ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- TABLE: editorials
-- One editorial per problem (1:1).
-- ---------------------------------------------------------------------------
CREATE TABLE editorials (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id        UUID          NOT NULL,
    content           TEXT,
    time_complexity   VARCHAR(50),
    space_complexity  VARCHAR(50),
    approaches        JSONB,         -- array of {title, description, complexity}
    author_id         UUID,          -- references auth-service users.id
    published         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_editorial_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE,

    CONSTRAINT editorials_problem_unique
        UNIQUE (problem_id)
);

COMMENT ON TABLE  editorials           IS 'Official editorial / explanation for a problem.';
COMMENT ON COLUMN editorials.approaches IS 'JSONB array: [{title, description, time_complexity, space_complexity}].';

-- Now we can add the FK from problems.editorial_id → editorials.id
ALTER TABLE problems
    ADD CONSTRAINT fk_problems_editorial
        FOREIGN KEY (editorial_id) REFERENCES editorials (id)
        ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- TABLE: solutions
-- Community and official solutions.
-- ---------------------------------------------------------------------------
CREATE TABLE solutions (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id   UUID          NOT NULL,
    user_id      UUID          NOT NULL,    -- references auth-service users.id
    language     VARCHAR(50),
    code         TEXT,
    is_official  BOOLEAN       NOT NULL DEFAULT FALSE,
    upvotes      INTEGER       NOT NULL DEFAULT 0 CHECK (upvotes >= 0),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_solution_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  solutions             IS 'Community and official problem solutions.';
COMMENT ON COLUMN solutions.is_official IS 'TRUE = authored/verified by AlgoVerse staff.';

-- ---------------------------------------------------------------------------
-- TABLE: user_problem_progress
-- Tracks per-user solve state for a problem.
-- ---------------------------------------------------------------------------
CREATE TABLE user_problem_progress (
    user_id             UUID                  NOT NULL,   -- references auth-service users.id
    problem_id          UUID                  NOT NULL,
    status              user_problem_status   NOT NULL DEFAULT 'NOT_STARTED',
    attempts            INTEGER               NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    first_solved_at     TIMESTAMPTZ,
    last_attempted_at   TIMESTAMPTZ,
    best_runtime_ms     INTEGER               CHECK (best_runtime_ms > 0),
    best_memory_mb      INTEGER               CHECK (best_memory_mb > 0),

    PRIMARY KEY (user_id, problem_id),

    CONSTRAINT fk_progress_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  user_problem_progress IS 'Per-user solve status and personal-best metrics for each problem.';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- problems
CREATE INDEX idx_problems_difficulty_status
    ON problems (difficulty, status);

CREATE INDEX idx_problems_status_published
    ON problems (id, slug, title, difficulty)
    WHERE status = 'PUBLISHED';

-- GIN index for full-text search on the stored tsvector
CREATE INDEX idx_problems_fts
    ON problems USING GIN (search_vector);

-- GIN index on tags array for @> and unnest queries
CREATE INDEX idx_problems_tags_gin
    ON problems USING GIN (tags);

-- GIN index on companies array
CREATE INDEX idx_problems_companies_gin
    ON problems USING GIN (companies);

CREATE INDEX idx_problems_created_by
    ON problems (created_by);

CREATE INDEX idx_problems_updated_at
    ON problems (updated_at DESC);

-- test_cases
CREATE INDEX idx_test_cases_problem_id
    ON test_cases (problem_id);

CREATE INDEX idx_test_cases_sample
    ON test_cases (problem_id)
    WHERE is_sample = TRUE;

-- problem_templates
CREATE INDEX idx_problem_templates_problem_id
    ON problem_templates (problem_id);

-- topics
CREATE INDEX idx_topics_parent_id
    ON topics (parent_id);

-- problem_topics
CREATE INDEX idx_problem_topics_topic_id
    ON problem_topics (topic_id);

-- editorials
CREATE INDEX idx_editorials_published
    ON editorials (problem_id)
    WHERE published = TRUE;

-- solutions
CREATE INDEX idx_solutions_problem_id
    ON solutions (problem_id, upvotes DESC);

CREATE INDEX idx_solutions_user_id
    ON solutions (user_id, created_at DESC);

-- user_problem_progress
CREATE INDEX idx_upp_user_status
    ON user_problem_progress (user_id, status);

CREATE INDEX idx_upp_problem_id
    ON user_problem_progress (problem_id);

-- ---------------------------------------------------------------------------
-- TRIGGER: auto-update updated_at on problems and editorials
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

CREATE TRIGGER trg_problems_updated_at
    BEFORE UPDATE ON problems
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_editorials_updated_at
    BEFORE UPDATE ON editorials
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- ---------------------------------------------------------------------------
-- TRIGGER: keep problems.submission_count accurate (optional fast-path)
-- This function is called from the execution-service via NOTIFY or direct update.
-- Left as a utility trigger; the execution service should call increment_submission_count().
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_increment_submission_count()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE problems
    SET submission_count = submission_count + 1
    WHERE id = NEW.problem_id;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_increment_submission_count IS
    'Increments problems.submission_count when a new submission row is inserted. '
    'Attach this trigger to execution-service submissions table if using a shared DB; '
    'otherwise invoke via RPC.';
