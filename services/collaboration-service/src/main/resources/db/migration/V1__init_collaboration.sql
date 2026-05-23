-- =============================================================================
-- AlgoVerse :: Collaboration Service — Initial Schema
-- PostgreSQL 16
-- Migration : V1__init_collaboration.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE room_type AS ENUM (
    'PAIR_PROGRAMMING',
    'MOCK_INTERVIEW',
    'STUDY_GROUP'
);

CREATE TYPE room_status AS ENUM (
    'WAITING',
    'ACTIVE',
    'ENDED'
);

CREATE TYPE participant_role AS ENUM (
    'HOST',
    'INTERVIEWER',
    'INTERVIEWEE',
    'OBSERVER'
);

-- ---------------------------------------------------------------------------
-- TABLE: rooms
-- Live collaboration sessions. Each room has a short human-readable join code.
-- ---------------------------------------------------------------------------
CREATE TABLE rooms (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(12)    NOT NULL,       -- short join code, e.g. 'ABC-123-XYZ'
    type             room_type      NOT NULL,
    problem_id       UUID,                           -- references problem-service problems.id; NULL for open sessions
    host_id          UUID           NOT NULL,        -- references auth-service users.id
    status           room_status    NOT NULL DEFAULT 'WAITING',
    max_participants INTEGER        NOT NULL DEFAULT 4
                     CHECK (max_participants BETWEEN 1 AND 20),
    settings         JSONB          NOT NULL DEFAULT '{}',
    started_at       TIMESTAMPTZ,                    -- NULL until first participant joins after host
    ended_at         TIMESTAMPTZ,                    -- NULL until session terminates
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT rooms_code_unique UNIQUE (code),

    CONSTRAINT rooms_ended_after_started
        CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
);

COMMENT ON TABLE  rooms              IS 'Collaboration room records for pair programming, mock interviews, and study groups.';
COMMENT ON COLUMN rooms.code         IS '12-char alphanumeric join code displayed in UI, e.g. ABX-7K2-MNP.';
COMMENT ON COLUMN rooms.settings     IS 'JSONB bag for runtime config: {language, theme, voice_enabled, timer_minutes, ...}.';
COMMENT ON COLUMN rooms.problem_id   IS 'Cross-service reference to problem-service; no FK enforced across services.';

-- ---------------------------------------------------------------------------
-- TABLE: room_participants
-- Tracks membership and role for each user in a room.
-- A user may re-join a room (left_at IS NOT NULL, new row with same room_id/user_id
-- is prevented by UNIQUE; instead update left_at=NULL on rejoin).
-- ---------------------------------------------------------------------------
CREATE TABLE room_participants (
    id          UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID               NOT NULL,
    user_id     UUID               NOT NULL,    -- references auth-service users.id
    role        participant_role   NOT NULL DEFAULT 'OBSERVER',
    joined_at   TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    left_at     TIMESTAMPTZ,                    -- NULL = currently in room

    CONSTRAINT fk_rp_room
        FOREIGN KEY (room_id) REFERENCES rooms (id)
        ON DELETE CASCADE,

    CONSTRAINT room_participants_unique
        UNIQUE (room_id, user_id)
);

COMMENT ON TABLE  room_participants         IS 'Participants within a collaboration room.';
COMMENT ON COLUMN room_participants.left_at IS 'NULL = active in room. Set on disconnect; cleared on reconnect.';

-- ---------------------------------------------------------------------------
-- TABLE: room_events
-- Append-only event log for room activity (cursor moves, code changes, chat, etc.).
-- Persisted for session replay and analytics.
-- ---------------------------------------------------------------------------
CREATE TABLE room_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id      UUID         NOT NULL,
    user_id      UUID,                       -- NULL for system events (e.g. room_started)
    event_type   VARCHAR(100) NOT NULL,      -- e.g. 'CODE_CHANGE', 'CURSOR_MOVE', 'CHAT_MESSAGE'
    payload      JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_re_room
        FOREIGN KEY (room_id) REFERENCES rooms (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE  room_events            IS 'Ordered event stream for room activity; used for session replay and analytics.';
COMMENT ON COLUMN room_events.event_type IS 'Discriminator: CODE_CHANGE, CURSOR_MOVE, CHAT_MESSAGE, LANGUAGE_CHANGE, EXECUTION_REQUESTED, USER_JOINED, USER_LEFT, ROOM_ENDED.';
COMMENT ON COLUMN room_events.payload    IS 'Event-specific JSONB payload (e.g. for CODE_CHANGE: {op, position, text}).';

-- ---------------------------------------------------------------------------
-- TABLE: crdt_snapshots
-- Periodic snapshots of the collaborative editor CRDT state.
-- Enables new participants to fast-forward without replaying all events.
-- ---------------------------------------------------------------------------
CREATE TABLE crdt_snapshots (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id        UUID          NOT NULL,
    snapshot_data  BYTEA         NOT NULL,    -- serialised CRDT state (e.g. Y.js update)
    version        BIGINT        NOT NULL,    -- monotonically increasing per room
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_cs_room
        FOREIGN KEY (room_id) REFERENCES rooms (id)
        ON DELETE CASCADE,

    -- Each version must be unique per room
    CONSTRAINT crdt_snapshots_room_version_unique
        UNIQUE (room_id, version)
);

COMMENT ON TABLE  crdt_snapshots              IS 'Periodic CRDT state snapshots for efficient late-joiner catch-up.';
COMMENT ON COLUMN crdt_snapshots.snapshot_data IS 'Binary-encoded CRDT snapshot (Y.js encoded state vector or update blob).';
COMMENT ON COLUMN crdt_snapshots.version       IS 'Logical clock / sequence number from the CRDT runtime.';

-- ---------------------------------------------------------------------------
-- INDEXES
-- ---------------------------------------------------------------------------

-- rooms
CREATE UNIQUE INDEX idx_rooms_code
    ON rooms (code);

CREATE INDEX idx_rooms_host_id
    ON rooms (host_id, created_at DESC);

CREATE INDEX idx_rooms_type_status
    ON rooms (type, status);

-- Partial index: only currently active rooms (hot path for join/discovery)
CREATE INDEX idx_rooms_active
    ON rooms (code, type, created_at DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_rooms_problem_id
    ON rooms (problem_id)
    WHERE problem_id IS NOT NULL;

-- room_participants
CREATE INDEX idx_rp_room_id
    ON room_participants (room_id);

CREATE INDEX idx_rp_user_id
    ON room_participants (user_id, joined_at DESC);

-- Partial index: currently connected participants
CREATE INDEX idx_rp_active
    ON room_participants (room_id, user_id)
    WHERE left_at IS NULL;

-- room_events
CREATE INDEX idx_re_room_created
    ON room_events (room_id, created_at ASC);    -- ASC for replay ordering

CREATE INDEX idx_re_room_event_type
    ON room_events (room_id, event_type, created_at DESC);

CREATE INDEX idx_re_user_id
    ON room_events (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- crdt_snapshots
CREATE INDEX idx_cs_room_version
    ON crdt_snapshots (room_id, version DESC);   -- latest snapshot first

-- ---------------------------------------------------------------------------
-- FUNCTION: generate_room_code
-- Generates a unique, human-readable 12-character room join code.
-- Format: XXX-XXX-XXX (3 groups of 3 uppercase alphanum chars)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION generate_room_code()
RETURNS VARCHAR(12)
LANGUAGE plpgsql
AS $$
DECLARE
    v_code     VARCHAR(12);
    v_exists   BOOLEAN := TRUE;
    v_chars    TEXT    := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- exclude I,O,0,1 for readability
    v_len      INT     := LENGTH(v_chars);
    v_attempt  INT     := 0;
BEGIN
    WHILE v_exists AND v_attempt < 100 LOOP
        v_code := CONCAT(
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            '-',
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            '-',
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1),
            SUBSTRING(v_chars FROM (FLOOR(RANDOM() * v_len)::INT + 1) FOR 1)
        );
        SELECT EXISTS(SELECT 1 FROM rooms WHERE code = v_code) INTO v_exists;
        v_attempt := v_attempt + 1;
    END LOOP;

    IF v_exists THEN
        RAISE EXCEPTION 'Could not generate unique room code after 100 attempts';
    END IF;

    RETURN v_code;
END;
$$;

COMMENT ON FUNCTION generate_room_code IS
    'Generates a collision-free XXX-XXX-XXX room code from an unambiguous character set. '
    'Call at INSERT time: INSERT INTO rooms (code, ...) VALUES (generate_room_code(), ...).';

-- ---------------------------------------------------------------------------
-- FUNCTION: get_latest_crdt_snapshot
-- Returns the most recent snapshot for a room (for late-joiner initialisation).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_latest_crdt_snapshot(p_room_id UUID)
RETURNS TABLE (snapshot_data BYTEA, version BIGINT)
LANGUAGE sql
STABLE
AS $$
    SELECT snapshot_data, version
    FROM crdt_snapshots
    WHERE room_id = p_room_id
    ORDER BY version DESC
    LIMIT 1;
$$;

COMMENT ON FUNCTION get_latest_crdt_snapshot IS
    'Returns the latest CRDT snapshot blob and version for a room. '
    'Used by the WebSocket server to initialise new participants.';

-- ---------------------------------------------------------------------------
-- TRIGGER: enforce max_participants constraint on room_participants insert
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_check_room_capacity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_count     INTEGER;
    v_max       INTEGER;
    v_status    room_status;
BEGIN
    SELECT max_participants, status
    INTO v_max, v_status
    FROM rooms
    WHERE id = NEW.room_id;

    IF v_status = 'ENDED' THEN
        RAISE EXCEPTION 'Cannot join an ended room (room_id: %)', NEW.room_id;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM room_participants
    WHERE room_id = NEW.room_id AND left_at IS NULL;

    IF v_count >= v_max THEN
        RAISE EXCEPTION 'Room is at full capacity (max_participants: %, room_id: %)',
            v_max, NEW.room_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_room_capacity_check
    BEFORE INSERT ON room_participants
    FOR EACH ROW
    EXECUTE FUNCTION fn_check_room_capacity();

COMMENT ON FUNCTION fn_check_room_capacity IS
    'Prevents joining full or ended rooms at the database level. '
    'Complements application-layer validation.';
