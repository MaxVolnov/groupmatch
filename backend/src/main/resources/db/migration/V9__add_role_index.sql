-- V9: Admin role — ensure role column exists (IF NOT EXISTS is safe against V6) + index
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

CREATE INDEX IF NOT EXISTS idx_app_user_role ON app_user(role);
