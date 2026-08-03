-- Токен для подписки на календарь группы (.ics-фид).
-- Календарные клиенты не умеют слать Authorization, поэтому доступ к фиду
-- даёт неугадываемый токен в URL — та же модель, что у ссылок-приглашений.
ALTER TABLE grp
    ADD COLUMN calendar_token TEXT;

-- Бэкфилл существующих групп: 48 hex-символов, как у токенов в Java.
-- Через gen_random_uuid(), а не gen_random_bytes(): второе живёт в pgcrypto,
-- которого на инстансе может не быть, а UUID v4 в PG13+ встроен и берётся
-- из криптостойкого источника.
UPDATE grp
   SET calendar_token = substr(
           replace(gen_random_uuid()::text, '-', '') ||
           replace(gen_random_uuid()::text, '-', ''), 1, 48)
 WHERE calendar_token IS NULL;

ALTER TABLE grp
    ALTER COLUMN calendar_token SET NOT NULL;

CREATE UNIQUE INDEX idx_grp_calendar_token ON grp(calendar_token);
