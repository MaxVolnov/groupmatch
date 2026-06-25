-- V6 created a billing stub with a different schema (UNIQUE user_id, stripe_sub_id, valid_until).
-- Drop it along with its trigger and index before creating the real table.
DROP TABLE IF EXISTS subscription CASCADE;

CREATE TABLE subscription (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    plan                 VARCHAR(10) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    yookassa_payment_id  VARCHAR(64),
    amount_kopecks       BIGINT      NOT NULL,
    period_months        INT         NOT NULL DEFAULT 1,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_user_id ON subscription(user_id);
CREATE INDEX idx_subscription_expires_at ON subscription(expires_at) WHERE status = 'ACTIVE';
