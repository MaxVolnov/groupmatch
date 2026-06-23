-- V11: Admin feedback inbox — resolve/unresolve support
ALTER TABLE feedback
    ADD COLUMN IF NOT EXISTS is_resolved  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS resolved_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resolved_by  UUID REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_feedback_resolved    ON feedback(is_resolved);
CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback(created_at DESC);
