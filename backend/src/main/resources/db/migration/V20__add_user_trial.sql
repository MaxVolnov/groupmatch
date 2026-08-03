-- Псевдо-премиум для первых пользователей: при регистрации выдаётся PRO
-- на ограниченный срок. NULL = триала не было (гости, старые аккаунты)
-- либо он уже отработан PlanExpiryJob'ом.
ALTER TABLE app_user
    ADD COLUMN trial_expires_at TIMESTAMPTZ;

-- Джоб ищет только аккаунты с непустым и истёкшим триалом.
CREATE INDEX idx_app_user_trial_expires
    ON app_user(trial_expires_at)
    WHERE trial_expires_at IS NOT NULL;
