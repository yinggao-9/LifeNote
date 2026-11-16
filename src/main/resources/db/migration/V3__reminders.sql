-- ============================================================
-- V3: in-app reminders produced by the scheduled jobs.
-- DEADLINE rows belong to one task; EVENING rows are a daily
-- digest per user. Partial unique indexes make generation
-- idempotent, so a retried job run never duplicates a row.
-- ============================================================

CREATE TABLE reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id         UUID REFERENCES tasks(id) ON DELETE CASCADE,
    type            VARCHAR(16) NOT NULL CHECK (type IN ('DEADLINE','EVENING')),
    remind_for_date DATE NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reminders_user_read ON reminders (user_id, is_read, created_at DESC);

-- one deadline reminder per task per due date
CREATE UNIQUE INDEX uk_reminders_deadline
    ON reminders (user_id, task_id, remind_for_date)
    WHERE type = 'DEADLINE';

-- one evening digest per user per day
CREATE UNIQUE INDEX uk_reminders_evening
    ON reminders (user_id, remind_for_date)
    WHERE type = 'EVENING';
