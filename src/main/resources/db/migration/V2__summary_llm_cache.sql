-- ============================================================
-- V2: cache for the LLM-written monthly summary paragraph.
-- The counts are always recomputed from tasks/postponements;
-- only the generated text is stored, keyed by user + month, so
-- the summary page stays fast after the first generation.
-- ============================================================

CREATE TABLE summary_llm_cache (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month        VARCHAR(7) NOT NULL,          -- yyyy-MM
    content      TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_summary_llm_cache_user_month UNIQUE (user_id, month)
);
