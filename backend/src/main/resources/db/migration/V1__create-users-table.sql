CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    display_name    TEXT NOT NULL,
    tz_id           TEXT NOT NULL DEFAULT 'Europe/Moscow',
    plan            TEXT NOT NULL DEFAULT 'FREE',
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Constraints
    CONSTRAINT chk_user_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_user_plan CHECK (plan IN ('FREE', 'PRO', 'TEAM')),
    CONSTRAINT chk_user_display_name_length CHECK (char_length(display_name) BETWEEN 2 AND 50)
);

-- Unique index on email (only for non-blocked users for faster lookups)
CREATE UNIQUE INDEX idx_user_email ON app_user(email);
CREATE INDEX idx_user_email_active ON app_user(email) WHERE NOT is_blocked;

-- Trigger for auto-updating updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE app_user IS 'User accounts for GroupMatch platform';
COMMENT ON COLUMN app_user.tz_id IS 'IANA timezone identifier (e.g., Europe/Moscow)';
COMMENT ON COLUMN app_user.plan IS 'Subscription tier: FREE, PRO, or TEAM';