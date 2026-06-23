-- V10: Admin ban functionality — ban flag and reason for app_user
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS is_banned  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ban_reason TEXT;
