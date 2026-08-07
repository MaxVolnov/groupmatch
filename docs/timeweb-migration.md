# Миграция бэкенда Railway → Timeweb Cloud

**Дата:** 7 августа 2026
**Повод:** Railway заблокирован для российских пользователей на уровне DPI —
запросы к `groupmatch-production.up.railway.app` не проходят без VPN, включая
ответы в 517 байт, на трёх операторах и двух устройствах.
**Этап:** первый — перенос данных и подготовка конфига. Прод не трогаем,
Railway остаётся рабочим и единственным источником данных.

---

## Что не сделано из этой сессии и почему

Перенос данных и проверку Resend выполнить не удалось: окружение, в котором
готовился этот документ, изолировано egress-политикой.

| Действие | Итог |
|---|---|
| TCP на Railway Postgres `:59317` | не проходит (сырой TCP мимо HTTPS-прокси заблокирован) |
| TCP на Timeweb Postgres `:5432` | не проходит |
| TCP на Timeweb Valkey `:6379` | не проходит |
| HTTPS `api.resend.com` | `403` на CONNECT — отказ политики |
| HTTPS `st.timeweb.com` (за `ca.crt`) | `403` на CONNECT |
| Docker registry (слои базовых образов) | `403` |

Отдельно, уже независимо от сети: **`pg_dump` в окружении — 16.13, а оба
сервера PostgreSQL 18.** `pg_dump` отказывается снимать дамп с сервера новее
себя, так что даже при открытой сети дамп бы не получился. Версия клиента
должна быть **>= 18**.

Поэтому разделы «Перенос данных» и «Проверка Resend» ниже — инструкции к
исполнению, а не отчёт о сделанном.

---

## 0. Что удалось проверить

Проверялось на локальном стенде (PostgreSQL 16 + Redis с паролем) с
переменными окружения в том формате, в котором они поедут в App Platform.

**Схема — на реальной базе, поднятой миграциями с нуля:**

| Что | Значение |
|---|---|
| Flyway | версия 21, 21 запись, все `success = true`, без пропусков |
| Последовательностей (`pg_sequences`) | **0** |
| Расширений, кроме `plpgsql` | **нет** |
| Таблиц | 13 + `flyway_schema_history` |

Два следствия, снимающие часть рисков из задания:

- **Про sequences можно не беспокоиться.** Все первичные ключи — `UUID` с
  `DEFAULT gen_random_uuid()`, ни одного `SERIAL`/`BIGSERIAL` в схеме нет.
  Класс проблем «после `pg_dump` последовательность отстала и первая же
  вставка ловит конфликт ID» здесь не существует. Сверить всё равно стоит
  (вдруг что-то заводили руками мимо миграций) — запрос в разделе 1.4.
- **Расширения переносить не нужно.** `gen_random_uuid()` встроен в PostgreSQL
  начиная с 13-й, `pgcrypto` не требуется. `plpgsql` есть в любой базе по
  умолчанию. Проверить фактическое состояние источника — запрос в 1.4.

**Конфиг приложения:**

- `SPRING_REDIS_URL` в формате `redis://default:PASSWORD@host:6379`
  разбирается корректно: `RedisConfig` берёт всё после первого `:` в
  user-info как пароль, имя `default` отбрасывает — для Redis это и есть
  подключение пользователем по умолчанию. Проверено на инстансе с
  `requirepass`: ключи `refresh:*` записались, `health.redis = UP`.
  ⚠️ Разбор идёт через `URI.create()`, поэтому пароль со символами `@`, `:`,
  `/` или `?` сломает парсинг. Текущий пароль безопасен; при смене — либо
  избегать этих символов, либо чинить `RedisConfig`.
- Профиль `prod` активируется, DEBUG-строк в логе старта — 0.
- Flyway на уже актуальной схеме: `Current version of schema "public": 21` →
  `Schema "public" is up to date. No migration necessary.` Это ровно то
  поведение, которое ожидается на Timeweb после восстановления дампа.

---

## 1. Перенос данных PostgreSQL

Выполнять с машины, у которой есть доступ к обоим серверам. Нужен
`postgresql-client` версии **18 или новее**.

### 1.1. Сертификат

```bash
mkdir -p ~/timeweb && cd ~/timeweb
curl -fsSL https://st.timeweb.com/cloud-static/ca.crt -o timeweb-ca.crt
openssl x509 -in timeweb-ca.crt -noout -subject -dates   # проверить, что не просрочен
chmod 0400 timeweb-ca.crt
```

### 1.2. Снять дамп с Railway

Railway доступен только через VPN — снимать дамп из-под него.

```bash
export PGPASSWORD='<пароль Railway>'
pg_dump \
  --host=yamabiko.proxy.rlwy.net --port=59317 \
  --username=postgres --dbname=railway \
  --format=custom --no-owner --no-privileges --verbose \
  --file=groupmatch-$(date +%Y%m%d-%H%M).dump
```

`--format=custom` — чтобы восстанавливать параллельно и выборочно.
`--no-owner --no-privileges` — на Timeweb другой владелец (`gen_user`),
роли Railway там не существуют и восстановление на них споткнётся.

**Зафиксировать состояние источника до восстановления** — это половина сверки:

```bash
psql "host=yamabiko.proxy.rlwy.net port=59317 user=postgres dbname=railway" \
  -Atc "SELECT relname||'='||n_live_tup FROM pg_stat_user_tables ORDER BY relname" \
  > before.txt
# n_live_tup — оценка; для точности по ключевым таблицам:
psql "host=yamabiko.proxy.rlwy.net port=59317 user=postgres dbname=railway" -Atc "
  SELECT 'app_user='||(SELECT count(*) FROM app_user)
      ||' grp='||(SELECT count(*) FROM grp)
      ||' grp_member='||(SELECT count(*) FROM grp_member)
      ||' availability='||(SELECT count(*) FROM availability)
      ||' meeting='||(SELECT count(*) FROM meeting)
      ||' notification='||(SELECT count(*) FROM notification)
      ||' subscription='||(SELECT count(*) FROM subscription)"
```

### 1.3. Восстановить в Timeweb

```bash
export PGPASSWORD='<пароль gen_user>'
export PGSSLROOTCERT=~/timeweb/timeweb-ca.crt
export PGSSLMODE=verify-full

pg_restore \
  --host=3ab2ec80d220c002941ded41.twc1.net --port=5432 \
  --username=gen_user --dbname=groupmatch \
  --no-owner --no-privileges --exit-on-error --verbose \
  groupmatch-YYYYMMDD-HHMM.dump
```

`--exit-on-error` обязателен: без него `pg_restore` досыпает что получится и
выходит с нулевым кодом, а половина таблиц оказывается пустой.

Здесь `verify-full` уместен — подключаемся по публичному имени
`*.twc1.net`, которое сертификат и покрывает. В проде будет иначе, см. 3.2.

### 1.4. Сверка — обязательно, а не «вроде поднялось»

Прогнать **на обоих** серверах и сравнить построчно:

```sql
-- точные количества по всем таблицам
SELECT 'app_user' t, count(*) FROM app_user
UNION ALL SELECT 'grp', count(*) FROM grp
UNION ALL SELECT 'grp_member', count(*) FROM grp_member
UNION ALL SELECT 'availability', count(*) FROM availability
UNION ALL SELECT 'meeting', count(*) FROM meeting
UNION ALL SELECT 'invite', count(*) FROM invite
UNION ALL SELECT 'feedback', count(*) FROM feedback
UNION ALL SELECT 'notification', count(*) FROM notification
UNION ALL SELECT 'notification_preferences', count(*) FROM notification_preferences
UNION ALL SELECT 'email_verification_token', count(*) FROM email_verification_token
UNION ALL SELECT 'password_reset_token', count(*) FROM password_reset_token
UNION ALL SELECT 'subscription', count(*) FROM subscription
UNION ALL SELECT 'report', count(*) FROM report
ORDER BY t;

-- версия схемы: ждём 21, 21 запись, без пропусков и без failed
SELECT max(version::int) AS max_version,
       count(*)          AS records,
       bool_and(success) AS all_ok,
       count(*) FILTER (WHERE NOT success) AS failed
FROM flyway_schema_history WHERE version IS NOT NULL;

SELECT version FROM flyway_schema_history
WHERE version IS NOT NULL ORDER BY version::int;   -- глазами: 1..21 без дыр

-- последовательности: ожидаем пусто на обоих
SELECT schemaname, sequencename, last_value FROM pg_sequences WHERE schemaname='public';

-- расширения: ожидаем только plpgsql
SELECT extname, extversion FROM pg_extension ORDER BY extname;

-- выборочно содержимое (сравнить хеши — надёжнее, чем глазами)
SELECT md5(string_agg(id::text||email||plan||role, '|' ORDER BY id)) FROM app_user;
SELECT md5(string_agg(id::text||title||owner_id::text, '|' ORDER BY id)) FROM grp;
SELECT md5(string_agg(id::text||title||starts_at::text, '|' ORDER BY id)) FROM meeting;
```

Хеши по трём ключевым таблицам должны совпасть посимвольно. Если расходятся —
не восстанавливать поверх, а очистить базу и повторить: частичное
восстановление хуже пустого.

---

## 2. Проверка Resend с российского IP — блокирующая

**Не выполнена**, `api.resend.com` закрыт политикой окружения (403 на
CONNECT). Даже если бы прошёл — запрос ушёл бы с egress-адреса площадки, а не
из России, и на вопрос «доступен ли Resend с российского сервера» не ответил
бы. Это надо проверять **с самого сервера Timeweb**, ни с чего другого.

Как только сервер поднят:

```bash
# только чтение, писем не отправляет
curl -sS -w '\n[HTTP %{http_code}, %{time_total}s]\n' \
  https://api.resend.com/domains \
  -H "Authorization: Bearer $RESEND_API_KEY"
```

- `200` со списком доменов — Resend доступен, вопрос закрыт.
- `401` — ключ протух, но сеть есть (это уже другая проблема).
- Таймаут, `000`, обрыв TLS — **блокер**: отвалятся письма верификации,
  сброса пароля и напоминания о встречах, то есть регистрация целиком.

### Если Resend недоступен

Нужен HTTP API, а не только SMTP — исходящий 587 у хостеров часто закрыт, и
именно поэтому мы в своё время ушли с JavaMail на Resend HTTP.

| Сервис | HTTP API | Оценка замены |
|---|---|---|
| **Unisender Go** | да, `/email/send.json`, Bearer-ключ | ~2–3 ч. Ближайший аналог по модели: один POST с JSON, темплейты не обязательны. Нужна верификация домена и DKIM |
| **SendPulse** | да, REST + OAuth2-токен | ~4 ч. Дороже по коду: токен нужно получать и обновлять, это лишний слой в `EmailService` |
| **Mail.ru для бизнеса** | только SMTP | не подходит без открытого 587 |
| **Yandex Cloud Postbox** | да, но API совместим с AWS SES (SigV4) | ~5–6 ч. Подпись SigV4 руками или тянуть AWS SDK ради одного вызова |

Меняется только `EmailService` — он уже изолирован за одним интерфейсом,
шаблоны и логика вызовов не затрагиваются. Домен `groupmatch.app` уже
верифицирован в Resend; у нового провайдера верификацию и DKIM надо
проходить заново, это сутки на прогрев DNS.

---

## 3. Деплой на App Platform

### 3.1. Сборка

`backend/Dockerfile` уже готов: multi-stage, Java 25, рантайм на
`eclipse-temurin:25-jre-alpine`, запуск от непривилегированного пользователя.
Контекст сборки — **корень репозитория**, путь к файлу `backend/Dockerfile`
(так же собирал Railway).

Собрать и прогнать образ локально в этой сессии не вышло: реестр Docker
отдаёт 403 на слои базовых образов. Проверено то, что можно без образа, —
разделы 0 и 3.3.

### 3.2. SSL: почему `verify-ca`, а не `verify-full`

⚠️ **`verify-full` с приватным адресом работать не будет.** Этот режим
проверяет, что имя, к которому мы подключились, совпадает с CN/SAN
сертификата. В проде мы идём на `192.168.0.4` — IP-адрес, которого в
сертификате для `*.twc1.net` нет. Соединение упадёт на проверке имени.

Варианты и выбор:

| Режим | Шифрование | Проверка подлинности сервера | С приватным IP |
|---|---|---|---|
| `require` | да | **нет** | работает |
| `verify-ca` | да | да, по цепочке до CA | работает |
| `verify-full` | да | да + совпадение имени | **падает** |

Выбран **`verify-ca`**: канал шифруется и сервер подтверждается сертификатом
Timeweb, а проверка имени, которая на IP всё равно бессмысленна, снимается.
`require` не берём: он защищает от прослушивания, но не от подмены — а
внутри общей приватной сети провайдера сосед по сети это ровно тот сценарий,
от которого мы страхуемся. Разница в цене нулевая, оба требуют одного файла.

Сертификат кладётся в образ на этапе сборки (`ADD` в Dockerfile) по адресу
`/app/certs/timeweb-ca.crt`, драйвер получает его через `sslrootcert` в
JDBC-URL. Если билдер App Platform окажется без выхода наружу — положить
файл в `backend/certs/timeweb-ca.crt` и заменить `ADD` на `COPY`; сертификат
публичный, коммитить его можно, вопрос только в воспроизводимости сборки.

### 3.3. Переменные окружения для App Platform

Готово к вставке. Пароли подставить свои — в репозитории их нет и быть не
должно.

```
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://192.168.0.4:5432/groupmatch?sslmode=verify-ca&sslrootcert=/app/certs/timeweb-ca.crt
SPRING_DATASOURCE_USERNAME=gen_user
SPRING_DATASOURCE_PASSWORD=<пароль gen_user>

SPRING_REDIS_URL=redis://default:<пароль Valkey>@192.168.0.5:6379

JWT_SECRET=<тот же, что на Railway>

API_BASE_URL=https://api.groupmatch.app
APP_BASE_URL=https://groupmatch.app
CORS_ALLOWED_ORIGINS=https://groupmatch.app
MAIL_FROM=noreply@groupmatch.app
RESEND_API_KEY=<ключ Resend>

MONETIZATION_ENABLED=false
TRIAL_ENABLED=true
TRIAL_DURATION_MONTHS=3
GUEST_RETENTION_DAYS=90

TRUSTED_PROXIES=127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10,fd00::/8
```

Замечания к списку:

- **`JWT_SECRET` обязан совпасть с Railway.** Иначе все выданные access-токены
  станут невалидными и пользователи разлогинятся в момент переключения.
- **Обе базы — по приватным адресам.** Публичные (`*.twc1.net`,
  `200.165.224.188`) нужны только для миграции с внешней машины; в проде
  через них ходить незачем, это лишний хоп наружу и лишняя поверхность.
- **`TRUSTED_PROXIES` укорочен** относительно текущего значения на Railway:
  диапазоны Cloudflare убраны. Они нужны, только пока `api.groupmatch.app`
  проксируется через Cloudflare. Если на Timeweb схема останется с
  Cloudflare — вернуть полный список из `docs/api-subdomain-migration.md`,
  шаг 4, **иначе rate-limit начнёт считать всех пользователей по IP эджа**.
  Если Cloudflare убирается — оставить как здесь.
- **`SPRING_REDIS_URL`, а не `SPRING_DATA_REDIS_URL`.** Имя нестандартное, но
  рабочее: связку держит явный плейсхолдер в `application.yml`. Подробности и
  эксперимент — `docs/session-audit-2026-08.md`, раздел 9. Не «причёсывать».
- Valkey слушает 6379 без TLS — внутри приватной сети приемлемо, наружу порт
  публиковать не надо.

---

## 4. Порядок переключения (следующий этап)

Railway остаётся источником данных, пока не пройдены все проверки.

1. Перенести данные (раздел 1), сверить (1.4).
2. Поднять приложение на Timeweb, **не переключая DNS**. Проверить по
   временному адресу App Platform: `/actuator/health` → `UP`,
   Flyway → `Schema "public" is up to date`.
3. Проверить Resend с сервера (раздел 2). При отказе — дальше не идти, сперва
   решить вопрос с почтой.
4. Прогнать `scripts/smoke-test.sh <временный-адрес> https://groupmatch.app`.
5. **Проверить из проблемной сети** — с того же домашнего интернета, где
   Railway не открывался. Без этого весь переезд смысла не имеет: если DPI
   режет и Timeweb, мы поменяли одну недоступную площадку на другую.
6. Только после этого переключить `api.groupmatch.app` на Timeweb и
   `VITE_API_URL` на фронте.
7. Railway не удалять минимум неделю — это единственный откат, и данные там
   на момент переключения свежее любого дампа.

Между шагом 1 и шагом 6 пользователи продолжают писать в базу Railway, так
что перед самим переключением дамп надо снять и восстановить **повторно**,
уже с коротким окном. Первый перенос — репетиция и проверка совместимости.
