CREATE TABLE sysdesign_problems (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(255) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    difficulty  VARCHAR(10)  NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    category    VARCHAR(100) NOT NULL,
    description_md TEXT,
    requirements   TEXT[],
    is_published   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_diagrams (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    problem_id  UUID NOT NULL REFERENCES sysdesign_problems(id),
    title       VARCHAR(255) NOT NULL DEFAULT 'My Solution',
    version     INTEGER NOT NULL DEFAULT 1,
    is_submitted BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_diagrams_user_id ON user_diagrams(user_id);
CREATE INDEX idx_user_diagrams_problem ON user_diagrams(problem_id);
