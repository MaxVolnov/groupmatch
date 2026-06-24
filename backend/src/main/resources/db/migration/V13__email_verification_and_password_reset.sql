-- Email verification flag on app_user
ALTER TABLE app_user
    ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing non-guest users are considered verified (retroactive)
UPDATE app_user
SET is_email_verified = TRUE
WHERE is_guest = FALSE;

-- Email verification tokens
CREATE TABLE email_verification_token (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_evt_token   ON email_verification_token(token);
CREATE INDEX idx_evt_user_id ON email_verification_token(user_id);

-- Password reset tokens
CREATE TABLE password_reset_token (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_prt_token   ON password_reset_token(token);
CREATE INDEX idx_prt_user_id ON password_reset_token(user_id);
