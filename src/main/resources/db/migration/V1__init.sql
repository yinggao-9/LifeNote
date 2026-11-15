-- ============================================================
-- LifeNote initial schema.
-- Table layout is owned by Flyway; entities are only the Java mapping of these tables.
-- ============================================================

-- ---------- existing tables (kept in sync with User / UserSetting entities) ----------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_users_email ON users (lower(email));

CREATE TABLE user_settings (
    user_id                UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    time_zone              VARCHAR(50) NOT NULL DEFAULT 'Europe/Dublin',
    evening_reminder_time  TIME        NOT NULL DEFAULT '21:00:00',
    email_reminders_enabled BOOLEAN    NOT NULL DEFAULT false,
    ai_model               VARCHAR(30) NOT NULL DEFAULT 'default'
);

-- ---------- 1. source material (pasted text, uploaded image/PDF) ----------
CREATE TABLE documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    doc_type          VARCHAR(16) NOT NULL CHECK (doc_type IN ('TEXT','IMAGE','PDF')),
    s3_key            VARCHAR(512),
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    file_size         BIGINT,
    raw_text          TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING_CONFIRM'
                      CHECK (status IN ('PENDING_CONFIRM','CONFIRMED','CANCELLED','FAILED')),
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_user_created ON documents (user_id, created_at DESC);

-- ---------- 2. audit trail for every LLM call ----------
CREATE TABLE ai_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    model          VARCHAR(64)  NOT NULL,
    prompt_version VARCHAR(32)  NOT NULL,
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    raw_output     TEXT,
    error_message  TEXT,
    duration_ms    INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_runs_document ON ai_runs (document_id, created_at DESC);

-- ---------- 3. AI drafts awaiting user confirmation ----------
CREATE TABLE task_candidates (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id        UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    run_id             UUID NOT NULL REFERENCES ai_runs(id)   ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    title              VARCHAR(200) NOT NULL,
    due_date           DATE,
    due_time           TIME,
    place              VARCHAR(200),
    documents_to_bring JSONB,
    notes              TEXT,
    confidence         DOUBLE PRECISION,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','CONFIRMED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_candidates_document ON task_candidates (document_id);

-- ---------- 4. confirmed tasks (calendar, board and .ics all read from here) ----------
CREATE TABLE tasks (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                  VARCHAR(200) NOT NULL,
    notes                  TEXT,
    due_date               DATE NOT NULL,
    due_time               TIME,
    place                  VARCHAR(200),
    documents_to_bring     JSONB,
    status                 VARCHAR(16) NOT NULL DEFAULT 'TODO'
                           CHECK (status IN ('TODO','DONE','CANCELLED','POSTPONED')),
    source                 VARCHAR(16) NOT NULL CHECK (source IN ('MANUAL','QUICK','AI')),
    completed_at           TIMESTAMPTZ,
    postponed_from_task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_user_due        ON tasks (user_id, due_date);
CREATE INDEX idx_tasks_user_status_due ON tasks (user_id, status, due_date);

-- ---------- 5. task <-> source material (many to many) ----------
CREATE TABLE task_documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES tasks(id)     ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_task_document UNIQUE (task_id, document_id)
);
CREATE INDEX idx_task_documents_document ON task_documents (document_id);

-- ---------- 6. postponement log: one row per postpone action ----------
CREATE TABLE postponements (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id       UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    new_task_id   UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    old_due_date  DATE NOT NULL,
    old_due_time  TIME,
    new_due_date  DATE NOT NULL,
    new_due_time  TIME,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_postponements_user ON postponements (user_id, created_at DESC);
