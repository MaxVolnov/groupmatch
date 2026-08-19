# Финальное переключение Railway → Timeweb

> **Исторический документ.** Миграция завершена 15.08.2026, действующее описание —
> `docs/prod-runbook.md`. Оставлен для восстановления контекста решений.


**Дата подготовки:** 14 августа 2026
**Состояние:** бэкенд поднят на `https://maxvolnov-groupmatch-4a45.twc1.net`,
`/actuator/health` отдаёт 200 из России без VPN, все компоненты UP.
**Осталось:** финальный перенос данных и переключение домена.

Первый перенос был репетицией: пользователи всё это время писали в Railway,
поэтому данные в Timeweb устарели и подлежат замене целиком.

---

## 0. Что проверено, а что нет

**Смок-тест по новому адресу выполнить не удалось.** Egress-политика окружения
блокирует все три хоста на уровне CONNECT:

```
maxvolnov-groupmatch-4a45.twc1.net:443 → gateway answered 403 to CONNECT
groupmatch.app:443                     → gateway answered 403 to CONNECT
api.groupmatch.app:443                 → gateway answered 403 to CONNECT
```

Скрипт остановился на первой же секции (`1. POST /api/v1/auth/signup`) с кодом
возврата curl 56 — это обрыв соединения, а не ответ приложения. **Ни одна
секция не отработала**, поэтому сказать «что прошло, а что упало» нельзя: не
прошло ничего и ничего не проверено. Прогонять смок-тест придётся с машины,
у которой есть доступ наружу:

```bash
./scripts/smoke-test.sh https://maxvolnov-groupmatch-4a45.twc1.net https://groupmatch.app
```

Что **проверено** в этой сессии: процедура переноса данных (раздел 2) —
прогнана целиком на живых базах, включая сравнение вариантов restore поверх
непустой базы. Все SQL-запросы из раздела 2.5 выполнены на схеме, поднятой
миграциями, и возвращают результат.

---

## 1. Остатки Railway в коде

Функционально значим **ровно один** — остальное комментарии и документация.

### Требует правки

| Файл | Что там | Почему важно |
|---|---|---|
| `frontend/legal.html:118` | «Данные хранятся на серверах **Railway (США)**» | Утверждение в политике конфиденциальности, которое после переезда становится ложным. Данные будут в России, у Timeweb |

Формулировка на замену — за вами, но фактически станет так: хранение на
серверах Timeweb Cloud (Россия). Для 152-ФЗ это улучшение, а не проблема, —
локализация персональных данных как раз выполняется. Я эту строку **не
трогал**: правка публичного юридического текста — ваше решение, а не моё.

### Комментарии в коде — не ломают ничего, но вводят в заблуждение

| Файл | Строка |
|---|---|
| `backend/.../config/SecurityConfig.java` | 158 — «The app runs behind Railway's proxy» (сам код от площадки не зависит) |
| `backend/.../service/GroupCalendarService.java` | 100 — «фронт на Vercel, бэкенд на Railway» |
| `backend/.../resources/application.yml` | 33, 90, 112 — пояснения про переменные Railway |
| `backend/.../security/ClientIpResolverTest.java` | 92–267 — сценарии «Cloudflare → Railway» в комментариях к тестам |
| `scripts/smoke-test.sh` | 6, 14–17 — примеры вызова с прямым адресом Railway |
| `.env.example` | 17–18, 33 — «было `*.up.railway.app`» |

### Документация — историческая, править не нужно

`docs/api-subdomain-migration.md`, `docs/timeweb-migration.md`,
`docs/development-plan.md`. Это записи о том, как было; переписывать их задним
числом — терять историю решений.

### Чего нет

- **Хардкода адреса бэкенда во фронтенде нет.** В `frontend/src` и HTML-файлах
  встречается только `https://groupmatch.app` — канонические ссылки и OG-теги,
  они относятся к фронту и не меняются. API-адрес приходит из `VITE_API_URL`.
- **Дефолты в `scripts/smoke-test.sh` уже правильные**: `BASE_URL` =
  `https://api.groupmatch.app`, `FRONTEND_URL` = `https://groupmatch.app`.
  Прямой адрес Railway упомянут только в комментарии-примере.

---

## 2. Финальный перенос данных

### 2.1. Подготовка окружения

```bash
export PG18=/opt/homebrew/opt/postgresql@18/bin
export CERT="$HOME/.cloud-certs/root.crt"
export STAMP=$(date +%Y%m%d-%H%M)

# Источник — Railway. Прокси Railway отдаёт сертификат, который не проверяется
# публичным CA, поэтому require: шифруем канал, подлинность не проверяем.
# Для одноразовой выгрузки это приемлемо, для постоянного соединения — нет.
export SRC="postgresql://postgres:ПАРОЛЬ_RAILWAY@yamabiko.proxy.rlwy.net:59317/railway?sslmode=require"

# Цель — Timeweb, публичный адрес. Здесь verify-full: имя в сертификате есть,
# проверяем полностью. (В проде приложение ходит по приватному IP и там
# verify-ca — см. timeweb-migration.md, 3.2.)
export DST="postgresql://gen_user:ПАРОЛЬ_GEN_USER@3ab2ec80d220c002941ded41.twc1.net:5432/groupmatch?sslmode=verify-full&sslrootcert=$CERT"

"$PG18/pg_dump" --version   # должно быть 18.x — сервера тоже 18
```

### 2.2. Остановить запись в Railway

Иначе между дампом и переключением пользователи допишут строки, которые
потеряются. Самый простой способ — остановить бэкенд-сервис в Railway
(пауза деплоя). Пользователи в этот момент увидят ошибки, поэтому окно должно
быть коротким: по замерам ниже всё вместе занимает минуты.

Фронтенд на Vercel в этот момент трогать не нужно — он просто не достучится
до API.

### 2.3. Снять дампы

```bash
# Боевые данные с Railway
"$PG18/pg_dump" --dbname="$SRC" \
  --format=custom --no-owner --no-privileges --verbose \
  --file="$HOME/groupmatch-final-$STAMP.dump"

# Страховка: что сейчас лежит в Timeweb, перед тем как это стереть.
# Стоит секунд, а спасает от «а если дамп источника окажется битым».
"$PG18/pg_dump" --dbname="$DST" \
  --format=custom --no-owner --no-privileges \
  --file="$HOME/timeweb-before-$STAMP.dump"

ls -lh "$HOME"/groupmatch-final-$STAMP.dump "$HOME"/timeweb-before-$STAMP.dump
```

Дампы держать **вне репозитория**. В `.gitignore` закрыты `*.dump`, `*.pgdump`,
`*.backup` и `dumps/`, но домашний каталог надёжнее.

### 2.4. Очистить цель и восстановить

⚠️ **Restore поверх непустой базы без очистки падает.** Проверено:

```
pg_restore: error: could not execute query:
  ERROR: function "add_owner_as_member" already exists with same argument types
```

Обратите внимание — падает на **функции**, а не на таблице. В схеме есть
триггерные функции (`add_owner_as_member`, `increment_group_version`,
`update_updated_at_column`), и они конфликтуют раньше, чем дело доходит до
данных. Именно поэтому «просто накатить сверху» не работает.

Выбран вариант **DROP SCHEMA + CREATE SCHEMA**:

```bash
# Сначала — кто владеет схемой. Если владелец pg_database_owner, а gen_user
# владеет базой groupmatch, права на DROP есть (проверено на стенде).
"$PG18/psql" "$DST" -c \
  "SELECT nspname, pg_get_userbyid(nspowner) AS owner FROM pg_namespace WHERE nspname='public'"

"$PG18/psql" "$DST" -v ON_ERROR_STOP=1 \
  -c "DROP SCHEMA public CASCADE" \
  -c "CREATE SCHEMA public"

"$PG18/pg_restore" --dbname="$DST" \
  --no-owner --no-privileges --exit-on-error --verbose \
  "$HOME/groupmatch-final-$STAMP.dump"
```

**Почему так, а не иначе.** Все три варианта прогнаны на живых базах:

| Вариант | Итог на стенде | Оценка |
|---|---|---|
| Просто `pg_restore` | падает на существующей функции | не годится |
| `DROP SCHEMA public CASCADE` + `CREATE SCHEMA` | restore OK, счётчики совпали, старых строк 0 | **выбран** |
| `pg_restore --clean --if-exists` | тоже отработал: 7 строк из 7, старых 0 | рабочая альтернатива |
| `DROP OWNED BY gen_user` | запасной путь, если нет прав на схему | на случай отказа |

`--clean --if-exists` действительно работает — я проверил, прежде чем его
отговаривать. Разница в другом: `--clean` удаляет только те объекты, которые
есть **в дампе**. Если в цели осталось что-то постороннее (недоделанный
эксперимент, объект от более новой миграции), оно переживёт restore и потом
всплывёт в самый неудобный момент. `DROP SCHEMA` не оставляет ничего, о чём мы
не знаем, а состояние после него ровно одно, а не «зависит от истории цели».

Риск `DROP SCHEMA` ровно один и он управляемый: если restore после дропа
упадёт, цель останется пустой. Поэтому страховочный дамп из 2.3 снимается
**до** дропа, а Railway на этот момент ещё жив и остаётся источником правды.

**Если `DROP SCHEMA` откажет по правам** (`must be owner of schema public`):

```bash
"$PG18/psql" "$DST" -v ON_ERROR_STOP=1 -c "DROP OWNED BY gen_user"
# затем тот же pg_restore
```

`DROP OWNED BY` сносит всё, чем владеет пользователь, не трогая саму схему.

### 2.5. Проверки после restore

Сначала — построчная сверка. Один и тот же запрос на обеих базах, результат
сравнивается автоматически, глазами ничего искать не надо.

Файл `counts.sql`:

```sql
SELECT 'app_user'                 AS table_name, count(*) AS rows FROM app_user
UNION ALL SELECT 'availability',             count(*) FROM availability
UNION ALL SELECT 'email_verification_token', count(*) FROM email_verification_token
UNION ALL SELECT 'feedback',                 count(*) FROM feedback
UNION ALL SELECT 'grp',                      count(*) FROM grp
UNION ALL SELECT 'grp_member',               count(*) FROM grp_member
UNION ALL SELECT 'invite',                   count(*) FROM invite
UNION ALL SELECT 'meeting',                  count(*) FROM meeting
UNION ALL SELECT 'notification',             count(*) FROM notification
UNION ALL SELECT 'notification_preferences', count(*) FROM notification_preferences
UNION ALL SELECT 'password_reset_token',     count(*) FROM password_reset_token
UNION ALL SELECT 'report',                   count(*) FROM report
UNION ALL SELECT 'subscription',             count(*) FROM subscription
UNION ALL SELECT 'flyway_schema_history',    count(*) FROM flyway_schema_history
ORDER BY 1;
```

```bash
"$PG18/psql" "$SRC" -qtA -F',' -f counts.sql > /tmp/counts-railway.csv
"$PG18/psql" "$DST" -qtA -F',' -f counts.sql > /tmp/counts-timeweb.csv
diff /tmp/counts-railway.csv /tmp/counts-timeweb.csv \
  && echo "СЧЁТЧИКИ СОВПАЛИ" || echo "РАСХОЖДЕНИЕ — не переключаться"
```

Дальше — состояние схемы и точечные проверки. Все запросы выполнены на реальной
схеме, синтаксис рабочий:

```sql
-- Flyway: ожидаем max_version = 21, records = 21, all_ok = t
SELECT max(version::int) AS max_version, count(*) AS records, bool_and(success) AS all_ok
  FROM flyway_schema_history;

-- Пропуски версий: ожидаем ноль строк
SELECT g AS missing_version
  FROM generate_series(1, 21) g
  LEFT JOIN flyway_schema_history f ON f.version = g::text
 WHERE f.version IS NULL;

-- app_user
SELECT count(*) AS total,
       count(*) FILTER (WHERE is_guest)       AS guests,
       count(*) FILTER (WHERE role = 'ADMIN') AS admins,
       count(DISTINCT email)                  AS distinct_emails,
       max(created_at)                        AS newest
  FROM app_user;

-- grp: without_token обязан быть 0, иначе сломаются .ics-подписки
SELECT count(*) AS total,
       count(*) FILTER (WHERE calendar_token IS NULL) AS without_token,
       count(DISTINCT owner_id)                       AS owners,
       max(created_at)                                AS newest
  FROM grp;

-- meeting
SELECT count(*) AS total,
       count(DISTINCT grp_id) AS groups_with_meetings,
       min(starts_at)         AS earliest,
       max(starts_at)         AS latest
  FROM meeting;

-- Ожидаем: последовательностей 0 (все ключи UUID), расширений — только plpgsql
SELECT count(*) AS sequences FROM pg_sequences WHERE schemaname = 'public';
SELECT string_agg(extname, ', ') AS extensions FROM pg_extension;
```

`distinct_emails` должно совпасть с `total`, `newest` в `app_user` — быть не
старше момента остановки Railway. `without_token > 0` — стоп: значит поехали
данные, от которых зависят календарные подписки.

### 2.6. Перезапустить приложение на Timeweb

Не обязательно, но полезно: в логе должно быть
`Schema "public" is up to date. No migration necessary.` Если Flyway начнёт
что-то применять — значит restore прошёл не полностью, дальше не идти.

---

## 3. Чек-лист переключения

Порядок важен. После каждого шага — что проверить и куда откатываться.

### Шаг 0. Заранее, за сутки

- [ ] **Снизить TTL** A/CNAME-записи `api.groupmatch.app` в Cloudflare до 60
      секунд. Сделать это **за время старого TTL до переключения**, иначе
      откат будет ждать старый TTL, а не новый.
- [ ] Проверить, что `VITE_API_URL` на Vercel = `https://api.groupmatch.app`.
      Если да — **Vercel трогать не придётся вообще**: домен просто переедет на
      другой origin, фронт этого не заметит. Это минус одна движущаяся часть,
      не создавайте её на ровном месте.

### Шаг 1. Домен в Timeweb

- [ ] Добавить `api.groupmatch.app` как собственный домен приложения.
- [ ] Дождаться выпуска сертификата.

⚠️ Пока запись в Cloudflare **проксируется** (оранжевое облако), Timeweb не
сможет пройти HTTP-проверку: запрос до него не дойдёт. Порядок тот же, что был
с Railway (`docs/api-subdomain-migration.md`, шаг 1): сначала серое облако,
дождаться сертификата, потом при желании вернуть оранжевое.

**Откат:** удалить домен в Timeweb. На прод не влияет — DNS ещё смотрит в Railway.

### Шаг 2. Данные

- [ ] Раздел 2 целиком: остановить Railway, снять дампы, очистить, восстановить,
      сверить.

**Откат:** запустить Railway обратно. Данные в нём не тронуты — он всё это
время источник, а не приёмник.

### Шаг 3. Переменные окружения на Timeweb

- [ ] `API_BASE_URL=https://api.groupmatch.app`
- [ ] `APP_BASE_URL=https://groupmatch.app`
- [ ] `CORS_ALLOWED_ORIGINS=https://groupmatch.app`
- [ ] `JWT_SECRET` — **тот же, что на Railway**, иначе в момент переключения
      разлогинятся все.
- [ ] `TRUSTED_PROXIES` — зависит от облака в Cloudflare:
      - **оранжевое (проксируется)** → вернуть диапазоны Cloudflare из
        `docs/api-subdomain-migration.md`, шаг 4. Без них rate-limit посчитает
        всех пользователей одним IP эджа и заблокирует вход всем сразу.
      - **серое (только DNS)** → оставить как в `timeweb-migration.md`, 3.5.
- [ ] Перезапустить приложение, проверить `/actuator/health` по адресу
      `*.twc1.net` — все компоненты UP.

**Откат:** вернуть прежние значения. DNS ещё не переключён.

### Шаг 4. DNS

- [ ] В Cloudflare заменить текущую запись `api.groupmatch.app` (CNAME на
      Railway) на **A → `72.56.9.252`**.
- [ ] Дождаться распространения: `dig +short api.groupmatch.app`.

**Откат — главный:** вернуть прежний CNAME на Railway. С TTL 60 секунд это
минута. Ради этого и снижался TTL на шаге 0.

### Шаг 5. Проверка

- [ ] `curl -si https://api.groupmatch.app/actuator/health` → 200, все UP
- [ ] `./scripts/smoke-test.sh https://api.groupmatch.app https://groupmatch.app`
- [ ] Вход существующим аккаунтом — токены должны продолжить работать
      (это проверка `JWT_SECRET`)
- [ ] Создать группу, добавить слот, открыть тепловую карту
- [ ] Открыть `.ics`-подписку (раздел 4)
- [ ] **Проверить с домашнего интернета, где Railway не открывался.** Без этого
      весь переезд бессмысленен: если DPI режет и Timeweb, мы поменяли одну
      недоступную площадку на другую.

### Шаг 6. После

- [ ] Поднять TTL обратно (300–3600 секунд).
- [ ] Поправить `frontend/legal.html` (раздел 1) и выкатить фронт.
- [ ] **Railway не удалять минимум неделю.** Это единственный откат, и данные
      там на момент переключения свежее любого дампа.
- [ ] Через неделю без инцидентов — удалить сервис Railway и записи о нём.

---

## 4. Если сломаются `.ics` или CORS

Обе вещи завязаны на `API_BASE_URL`, и ломаются они по-разному.

### `.ics`-подписки

Ссылка на фид собирается в момент запроса из `app.api-base-url`
(`GroupCalendarService.feedBaseUrl()`), а если переменная пуста — из
`app.app-base-url`. Отсюда два разных отказа:

**`API_BASE_URL` не задан.** Фид начнёт выдаваться по адресу фронтенда
(`https://groupmatch.app/...`), где его никто не обслуживает. Симптом:
календарный клиент не может обновить подписку, в браузере по ссылке — 404 от
Vercel. Лечится установкой переменной; уже выданные ссылки перевыпускать не
надо, токен лежит в `grp.calendar_token` и переносится дампом.

**Ссылки, выданные со старым хостом.** Токен переживает перенос, а хост в уже
разосланной ссылке — нет. Подписки, оформленные через `api.groupmatch.app`,
переживут переключение прозрачно: домен тот же, изменился только адрес за ним.
Подписки, оформленные через прямой адрес Railway или через `*.twc1.net`, будут
работать, пока жив соответствующий хост, и умрут вместе с ним. Перечислить их
нельзя — URL нигде не хранится, генерируется на лету. Практический вывод:
не удалять Railway раньше, чем через неделю, и не раздавать ссылки с
`*.twc1.net` как постоянные.

Проверка после переключения:

```bash
# Ссылка должна начинаться с https://api.groupmatch.app
curl -s -H "Authorization: Bearer $TOKEN" \
  https://api.groupmatch.app/api/v1/groups/$GROUP_ID/calendar-subscription | jq .

# Сам фид — 200 и text/calendar
curl -si "https://api.groupmatch.app/api/v1/groups/$GROUP_ID/calendar.ics?token=$CAL_TOKEN" | head -5
```

### CORS

`CORS_ALLOWED_ORIGINS` — это список **источников (фронтенда)**, а не адресов
API. При переезде бэкенда он не меняется: фронт как был на
`https://groupmatch.app`, так и остался.

Сломать его можно двумя способами:
- **Забыть переменную на новой площадке.** Тогда браузер заблокирует все
  запросы: в консоли `No 'Access-Control-Allow-Origin' header`, при этом
  `curl` работает — он CORS не проверяет. Отсюда типичная ловушка «через curl
  всё хорошо, а приложение не работает».
- **Указать в `VITE_API_URL` адрес `*.twc1.net` для теста и забыть вернуть.**
  Само по себе это не сломает CORS (источник остаётся `groupmatch.app`), но
  привяжет прод к временному адресу.

Проверка — обязательно с заголовком `Origin`, иначе тест ничего не значит:

```bash
curl -si -H "Origin: https://groupmatch.app" \
  https://api.groupmatch.app/api/v1/auth/signin -X OPTIONS \
  -H "Access-Control-Request-Method: POST" | grep -i "access-control"
```

Ожидаем `Access-Control-Allow-Origin: https://groupmatch.app`. Пустой вывод —
переменная не доехала.
