-- ==========================================
-- V6: Системная роль пользователя + таблицы invite, subscription, report
-- ==========================================

-- 1. Добавляем системную роль в app_user
--    USER  — стандартный пользователь платформы
--    ADMIN — модератор; доступ к /api/v1/admin/**
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER'
        CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));

COMMENT ON COLUMN app_user.role IS 'Системная роль: USER (стандарт) или ADMIN (модератор)';

-- ==========================================
-- 2. INVITES
-- ==========================================
CREATE TABLE IF NOT EXISTS invite (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grp_id          UUID NOT NULL REFERENCES grp(id) ON DELETE CASCADE,
    token           TEXT NOT NULL UNIQUE,
    created_by      UUID NOT NULL REFERENCES app_user(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '30 days'),
    max_uses        INTEGER NOT NULL DEFAULT 0 CHECK (max_uses >= 0),   -- 0 = unlimited
    current_uses    INTEGER NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_invite_uses CHECK (current_uses <= max_uses OR max_uses = 0)
);

-- Уникальный индекс только на активные инвайты (не отозванные)
CREATE UNIQUE INDEX IF NOT EXISTS idx_invite_token ON invite(token) WHERE NOT is_revoked;
CREATE INDEX IF NOT EXISTS idx_invite_grp ON invite(grp_id) WHERE NOT is_revoked;

COMMENT ON TABLE invite IS 'Ссылки-приглашения для вступления в группу';
COMMENT ON COLUMN invite.max_uses IS '0 = неограниченное количество использований';
COMMENT ON COLUMN invite.current_uses IS 'Количество успешных join по этой ссылке';

-- ==========================================
-- 3. SUBSCRIPTIONS (billing stub)
-- ==========================================
CREATE TABLE IF NOT EXISTS subscription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    plan            TEXT NOT NULL CHECK (plan IN ('FREE', 'PRO', 'TEAM')),
    valid_until     TIMESTAMPTZ,                     -- NULL для FREE
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    stripe_sub_id   TEXT,                            -- ID подписки в Stripe
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sub_user ON subscription(user_id);

-- Триггер для updated_at
CREATE TRIGGER trg_subscription_updated_at
    BEFORE UPDATE ON subscription
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE subscription IS 'Биллинг-заглушка; при интеграции Stripe — полная запись';

-- ==========================================
-- 4. REPORTS (moderation)
-- ==========================================
CREATE TABLE IF NOT EXISTS report (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID NOT NULL REFERENCES app_user(id),
    target_type     TEXT NOT NULL CHECK (target_type IN ('user', 'group', 'meeting')),
    target_id       UUID NOT NULL,
    reason          TEXT NOT NULL CHECK (reason IN ('spam', 'inappropriate', 'abuse', 'other')),
    details         TEXT CHECK (char_length(details) <= 1000),
    status          TEXT NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending', 'reviewed', 'resolved', 'dismissed')),
    reviewed_by     UUID REFERENCES app_user(id),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_report_status ON report(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_target ON report(target_type, target_id);

COMMENT ON TABLE report IS 'Жалобы пользователей на контент (модерация)';
COMMENT ON COLUMN report.target_type IS 'user | group | meeting';
COMMENT ON COLUMN report.status IS 'pending → reviewed → resolved | dismissed';
