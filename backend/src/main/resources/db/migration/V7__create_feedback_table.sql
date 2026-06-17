-- ==========================================
-- V7: Feedback submissions
-- ==========================================
CREATE TABLE IF NOT EXISTS feedback (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    category    TEXT        NOT NULL CHECK (category IN ('BUG', 'FEATURE_REQUEST', 'OTHER')),
    message     TEXT        NOT NULL CHECK (char_length(message) BETWEEN 10 AND 2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feedback_user_id    ON feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback(created_at DESC);

COMMENT ON TABLE feedback IS 'Отзывы и баг-репорты от пользователей';
COMMENT ON COLUMN feedback.category IS 'BUG | FEATURE_REQUEST | OTHER';
