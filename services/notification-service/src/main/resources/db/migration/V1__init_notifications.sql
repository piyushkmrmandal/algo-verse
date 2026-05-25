-- AlgoVerse :: Notification Service — Initial Schema

CREATE TABLE in_app_notifications (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(36)  NOT NULL,
    type             VARCHAR(100) NOT NULL,
    title            VARCHAR(80)  NOT NULL,
    body             VARCHAR(500) NOT NULL,
    data             JSONB,
    is_read          BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key  VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at          TIMESTAMPTZ,

    CONSTRAINT in_app_notifications_idempotency_key_unique UNIQUE (idempotency_key)
);

CREATE INDEX idx_ian_user_id          ON in_app_notifications (user_id, created_at DESC);
CREATE INDEX idx_ian_user_unread      ON in_app_notifications (user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_ian_idempotency_key  ON in_app_notifications (idempotency_key);
