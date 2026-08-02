-- Guest accounts were deleted by account AGE (created_at), so an actively used
-- guest account lost all its data on day 31 regardless of usage. Cleanup now
-- runs off last activity instead.

ALTER TABLE app_user
    ADD COLUMN last_activity_at TIMESTAMPTZ;

-- Existing rows: seed from updated_at when available, otherwise created_at, so
-- nobody is treated as instantly stale on the first run after deploy.
UPDATE app_user
   SET last_activity_at = COALESCE(updated_at, created_at, now());

ALTER TABLE app_user
    ALTER COLUMN last_activity_at SET NOT NULL,
    ALTER COLUMN last_activity_at SET DEFAULT now();

-- Supports the cleanup job's WHERE is_guest = true AND last_activity_at < :cutoff
CREATE INDEX idx_app_user_guest_last_activity
    ON app_user(last_activity_at)
    WHERE is_guest = true;
