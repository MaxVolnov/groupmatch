#!/usr/bin/env bash
set -euo pipefail

# Usage: smoke-test.sh [API_URL] [FRONTEND_URL]
#
# API и фронтенд живут на разных хостах (Timeweb / Vercel), поэтому базовых
# адреса два. Статические проверки (/promo, robots.txt, sitemap.xml) идут на
# фронтенд, всё остальное — на API.
#
# Переменные окружения:
#   SMOKE_MONETIZATION=1  — дополнительно прогнать проверки платных лимитов.
#                           Требует стенда с MONETIZATION_ENABLED=true и
#                           создаёт много данных: на проде НЕ включать.
# Прод-API — api.groupmatch.app (за Cloudflare). Прямой адрес приложения на
# площадке тоже отвечает и годится для сравнения «через прокси / напрямую»:
#   scripts/smoke-test.sh https://maxvolnov-groupmatch-4a45.twc1.net
BASE_URL="${1:-https://api.groupmatch.app}"
FRONTEND_URL="${2:-https://groupmatch.app}"
TIMESTAMP=$(date +%s)
EMAIL="smoke-${TIMESTAMP}@groupmatch-test.io"
PASSWORD="SmokeTest1!"
DISPLAY_NAME="Smoke Tester"

# ── helpers ──────────────────────────────────────────────────────────────────

if ! command -v jq &>/dev/null; then
  echo "❌ jq is required but not installed. Install with: brew install jq"
  exit 1
fi

check() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [ "$actual" -eq "$expected" ]; then
    echo "✅ $label → HTTP $actual"
  else
    echo "❌ FAILED: $label → expected HTTP $expected, got HTTP $actual"
    exit 1
  fi
}

# Проверка значения (не HTTP-кода): факт, ожидание, подпись.
check_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "✅ $label → $actual"
  else
    echo "❌ FAILED: $label → expected '$expected', got '$actual'"
    exit 1
  fi
}

check_contains() {
  local label="$1"
  local needle="$2"
  local haystack="$3"
  if printf '%s' "$haystack" | grep -qF -- "$needle"; then
    echo "✅ $label"
  else
    echo "❌ FAILED: $label → не найдено '$needle'"
    exit 1
  fi
}

check_not_empty() {
  local label="$1"
  local value="$2"
  if [ -n "$value" ] && [ "$value" != "null" ]; then
    echo "✅ $label → ${value:0:60}"
  else
    echo "❌ FAILED: $label → пусто"
    exit 1
  fi
}

http_status() {
  curl -s -o /dev/null -w "%{http_code}" "$1"
}

auth_header() {
  echo "Authorization: Bearer ${ACCESS_TOKEN}"
}

# ── 1. signup ─────────────────────────────────────────────────────────────────

echo ""
echo "── 1. POST /api/v1/auth/signup ──────────────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"displayName\":\"${DISPLAY_NAME}\"}")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "signup" 201 "$STATUS"

# Псевдо-премиум: новый аккаунт сразу PRO с датой окончания триала.
# Именно от этих двух полей зависят бейдж в шапке и баннер на Dashboard —
# если они не приехали, в UI проверять уже нечего.
check_eq "signup → plan" "PRO" "$(echo "$BODY" | jq -r '.plan')"
TRIAL_EXPIRES=$(echo "$BODY" | jq -r '.trialExpiresAt')
check_not_empty "signup → trialExpiresAt" "$TRIAL_EXPIRES"

# ── 2. signin ─────────────────────────────────────────────────────────────────

echo ""
echo "── 2. POST /api/v1/auth/signin ──────────────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/signin" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "signin" 200 "$STATUS"
ACCESS_TOKEN=$(echo "$BODY" | jq -r '.accessToken')
if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ FAILED: signin response missing accessToken"
  exit 1
fi
echo "   accessToken: ${ACCESS_TOKEN:0:40}..."

# ── 3. create group ───────────────────────────────────────────────────────────

echo ""
echo "── 3. POST /api/v1/groups ───────────────────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/groups" \
  -H "Content-Type: application/json" \
  -H "$(auth_header)" \
  -d "{\"title\":\"Smoke Test Group ${TIMESTAMP}\",\"description\":\"Auto-created by smoke test\",\"tzId\":\"Europe/Moscow\"}")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "create group" 201 "$STATUS"
GROUP_ID=$(echo "$BODY" | jq -r '.id')
if [ -z "$GROUP_ID" ] || [ "$GROUP_ID" = "null" ]; then
  echo "❌ FAILED: create group response missing id"
  exit 1
fi
echo "   group id: $GROUP_ID"

# ── 4. list groups ────────────────────────────────────────────────────────────

echo ""
echo "── 4. GET /api/v1/groups ────────────────────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/groups" \
  -H "$(auth_header)")
STATUS=$(echo "$RESP" | tail -n 1)
check "list groups" 200 "$STATUS"

# ── 5. get group by id ────────────────────────────────────────────────────────

echo ""
echo "── 5. GET /api/v1/groups/${GROUP_ID} ────────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/groups/${GROUP_ID}" \
  -H "$(auth_header)")
STATUS=$(echo "$RESP" | tail -n 1)
check "get group" 200 "$STATUS"

# ── 6. add availability slot ──────────────────────────────────────────────────

# tomorrow 10:00–11:00 UTC
STARTS_AT=$(date -u -d "+1 day 10:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+1d -v10H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")
ENDS_AT=$(date -u -d "+1 day 11:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+1d -v11H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")

echo ""
echo "── 6. POST /api/v1/groups/${GROUP_ID}/availability ──────────────────────"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/groups/${GROUP_ID}/availability" \
  -H "Content-Type: application/json" \
  -H "$(auth_header)" \
  -d "{\"startsAt\":\"${STARTS_AT}\",\"endsAt\":\"${ENDS_AT}\"}")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "add availability" 201 "$STATUS"
echo "   slot: $STARTS_AT → $ENDS_AT"

# ── 7. heatmap ────────────────────────────────────────────────────────────────

echo ""
echo "── 7. GET /api/v1/groups/${GROUP_ID}/availability/heatmap ───────────────"
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/groups/${GROUP_ID}/availability/heatmap" \
  -H "$(auth_header)")
STATUS=$(echo "$RESP" | tail -n 1)
check "heatmap" 200 "$STATUS"

# ── 8. trial premium через /me ────────────────────────────────────────────────

echo ""
echo "── 8. GET /api/v1/me — псевдо-премиум ───────────────────────────────────"
RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/me" -H "$(auth_header)")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "get me" 200 "$STATUS"
check_eq "me → plan" "PRO" "$(echo "$BODY" | jq -r '.plan')"
# До секунды: ответ signup сериализует Instant из памяти (наносекунды), а /me —
# то же значение, прочитанное из TIMESTAMPTZ (микросекунды). Дробная часть
# законно различается, сама дата — нет.
check_eq "me → trialExpiresAt совпадает с выданным при регистрации" \
  "${TRIAL_EXPIRES%%.*}" "$(echo "$BODY" | jq -r '.trialExpiresAt' | cut -d. -f1)"

# Триал должен быть в будущем — иначе баннер и бейдж не покажутся.
NOW_EPOCH=$(date -u +%s)
TRIAL_EPOCH=$(date -u -d "${TRIAL_EXPIRES}" +%s 2>/dev/null \
  || date -u -jf "%Y-%m-%dT%H:%M:%S" "${TRIAL_EXPIRES%%.*}" +%s)
if [ "$TRIAL_EPOCH" -gt "$NOW_EPOCH" ]; then
  echo "✅ триал активен → осталось $(( (TRIAL_EPOCH - NOW_EPOCH) / 86400 )) дн."
else
  echo "❌ FAILED: trialExpiresAt в прошлом (${TRIAL_EXPIRES})"
  exit 1
fi

# PRO означает безлимит по группам.
RESP=$(curl -s "${BASE_URL}/api/v1/me/plan" -H "$(auth_header)")
check_eq "me/plan → plan" "PRO" "$(echo "$RESP" | jq -r '.plan')"
check_eq "me/plan → groupLimit (безлимит)" "-1" "$(echo "$RESP" | jq -r '.groupLimit')"

# ── 9. .ics-подписка на календарь группы ──────────────────────────────────────

echo ""
echo "── 9. .ics-фид группы ───────────────────────────────────────────────────"

MEETING_START=$(date -u -d "+3 days 12:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+3d -v12H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")
MEETING_END=$(date -u -d "+3 days 13:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+3d -v13H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")
MEETING_TITLE="Smoke Meeting ${TIMESTAMP}"

RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/groups/${GROUP_ID}/meetings" \
  -H "Content-Type: application/json" -H "$(auth_header)" \
  -d "{\"title\":\"${MEETING_TITLE}\",\"startsAt\":\"${MEETING_START}\",\"endsAt\":\"${MEETING_END}\"}")
STATUS=$(echo "$RESP" | tail -n 1)
check "create meeting" 201 "$STATUS"

RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/groups/${GROUP_ID}/calendar-subscription" \
  -H "$(auth_header)")
BODY=$(echo "$RESP" | sed '$d')
STATUS=$(echo "$RESP" | tail -n 1)
check "calendar-subscription" 200 "$STATUS"
FEED_URL=$(echo "$BODY" | jq -r '.url')
WEBCAL_URL=$(echo "$BODY" | jq -r '.webcalUrl')
check_not_empty "feed url" "$FEED_URL"
check_contains "webcal-ссылка со схемой webcal://" "webcal://" "$WEBCAL_URL"

# Ссылка должна вести на API, а не на фронтенд: если APP_API_BASE_URL не задан
# в окружении, фид уедет на домен Vercel и календарь получит 404. Дешевле
# поймать здесь, чем в чужом Google Calendar.
FEED_STATUS=$(http_status "$FEED_URL")
if [ "$FEED_STATUS" != "200" ]; then
  echo "❌ FAILED: фид недоступен по выданной ссылке → HTTP $FEED_STATUS"
  echo "   url: $FEED_URL"
  echo "   Проверьте API_BASE_URL в окружении бэкенда."
  exit 1
fi
echo "✅ фид отдаётся по выданной ссылке → HTTP 200"

# Фид читается календарным клиентом — без Authorization, только токен в URL.
ICS=$(curl -s -H "Accept: text/calendar" "$FEED_URL")
check_contains "ics: BEGIN:VCALENDAR" "BEGIN:VCALENDAR" "$ICS"
check_contains "ics: METHOD:PUBLISH (это подписка, а не разовый файл)" "METHOD:PUBLISH" "$ICS"
check_contains "ics: REFRESH-INTERVAL" "REFRESH-INTERVAL" "$ICS"
check_contains "ics: созданная встреча внутри" "SUMMARY:${MEETING_TITLE}" "$ICS"

VEVENTS=$(printf '%s' "$ICS" | grep -c "^BEGIN:VEVENT" || true)
if [ "$VEVENTS" -ge 1 ]; then
  echo "✅ ics: VEVENT-ов в фиде → $VEVENTS"
else
  echo "❌ FAILED: в фиде нет ни одного VEVENT"
  exit 1
fi

# Парность BEGIN/END — простейший признак, что файл не обрезан.
VEVENT_ENDS=$(printf '%s' "$ICS" | grep -c "^END:VEVENT" || true)
check_eq "ics: BEGIN/END VEVENT парны" "$VEVENTS" "$VEVENT_ENDS"

# Фид живой: добавили встречу — она появилась по тому же URL.
SECOND_TITLE="Smoke Meeting 2 ${TIMESTAMP}"
SECOND_START=$(date -u -d "+5 days 12:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+5d -v12H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")
SECOND_END=$(date -u -d "+5 days 13:00" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
  || date -u -v+5d -v13H -v0M -v0S +"%Y-%m-%dT%H:%M:%SZ")
curl -s -o /dev/null -X POST "${BASE_URL}/api/v1/groups/${GROUP_ID}/meetings" \
  -H "Content-Type: application/json" -H "$(auth_header)" \
  -d "{\"title\":\"${SECOND_TITLE}\",\"startsAt\":\"${SECOND_START}\",\"endsAt\":\"${SECOND_END}\"}"
ICS2=$(curl -s "$FEED_URL")
VEVENTS2=$(printf '%s' "$ICS2" | grep -c "^BEGIN:VEVENT" || true)
check_eq "ics: подписка обновилась после новой встречи" "$((VEVENTS + 1))" "$VEVENTS2"

# Токен — единственная защита фида.
BAD_TOKEN_URL="${BASE_URL}/api/v1/groups/${GROUP_ID}/calendar.ics?token=$(printf '0%.0s' $(seq 1 48))"
check "ics с чужим токеном отклонён" 404 "$(http_status "$BAD_TOKEN_URL")"
check "ics без токена отклонён" 404 \
  "$(http_status "${BASE_URL}/api/v1/groups/${GROUP_ID}/calendar.ics")"

# ── 10. публичные статические страницы (фронтенд) ─────────────────────────────

echo ""
echo "── 10. Статика на ${FRONTEND_URL} ───────────────────────────────────────"

check "/promo" 200 "$(http_status "${FRONTEND_URL}/promo")"
check "/legal" 200 "$(http_status "${FRONTEND_URL}/legal")"
check "/about" 200 "$(http_status "${FRONTEND_URL}/about")"
check "/signup (цель CTA с лендинга)" 200 "$(http_status "${FRONTEND_URL}/signup")"

PROMO_HTML=$(curl -s "${FRONTEND_URL}/promo")
check_contains "promo: og:title" 'property="og:title"' "$PROMO_HTML"
check_contains "promo: og:description" 'property="og:description"' "$PROMO_HTML"
check_contains "promo: og:image" 'property="og:image"' "$PROMO_HTML"
check_contains "promo: twitter:card" 'name="twitter:card"' "$PROMO_HTML"
check_contains "promo: CTA ведёт на /signup" 'href="/signup"' "$PROMO_HTML"

# og:image должен реально отдаваться — иначе превью в мессенджере будет пустым.
OG_IMAGE=$(printf '%s' "$PROMO_HTML" | grep -o 'property="og:image" content="[^"]*"' \
  | head -1 | sed 's/.*content="//; s/"$//')
check_not_empty "promo: og:image url" "$OG_IMAGE"
# Мессенджеры не резолвят относительные пути — адрес обязан быть абсолютным.
case "$OG_IMAGE" in
  https://*) echo "✅ promo: og:image — абсолютный https-адрес" ;;
  *) echo "❌ FAILED: og:image не абсолютный https-адрес → $OG_IMAGE"; exit 1 ;;
esac
# А вот сам файл тянем с проверяемого стенда, а не с зашитого в тег хоста:
# иначе прогон против превью-окружения молча проверял бы прод.
OG_IMAGE_PATH="/$(printf '%s' "$OG_IMAGE" | cut -d/ -f4-)"
check "og:image отдаётся (${OG_IMAGE_PATH})" 200 "$(http_status "${FRONTEND_URL}${OG_IMAGE_PATH}")"

# Скриншоты: не плейсхолдеры, а реальные файлы. Плейсхолдер в вёрстке
# перекрывается картинкой, поэтому проверяем сам файл — код и размер.
for SHOT in screen-heatmap screen-availability screen-meeting; do
  SHOT_URL="${FRONTEND_URL}/promo/${SHOT}.png"
  SHOT_SIZE=$(curl -s -o /dev/null -w "%{size_download}" "$SHOT_URL")
  check "скриншот ${SHOT}.png" 200 "$(http_status "$SHOT_URL")"
  if [ "$SHOT_SIZE" -gt 10000 ]; then
    echo "✅ ${SHOT}.png не заглушка → $((SHOT_SIZE / 1024)) КБ"
  else
    echo "❌ FAILED: ${SHOT}.png подозрительно мал ($SHOT_SIZE байт) — похоже на заглушку"
    exit 1
  fi
done

# ── 11. robots.txt и sitemap.xml ──────────────────────────────────────────────

echo ""
echo "── 11. robots.txt / sitemap.xml ─────────────────────────────────────────"

check "robots.txt" 200 "$(http_status "${FRONTEND_URL}/robots.txt")"
ROBOTS=$(curl -s "${FRONTEND_URL}/robots.txt")
check_contains "robots: User-agent" "User-agent: *" "$ROBOTS"
check_contains "robots: ссылка на sitemap" "Sitemap:" "$ROBOTS"
check_contains "robots: закрыт /profile" "Disallow: /profile" "$ROBOTS"
check_contains "robots: закрыт /groups/" "Disallow: /groups/" "$ROBOTS"
check_contains "robots: открыт /promo" "Allow: /promo" "$ROBOTS"
if printf '%s' "$ROBOTS" | grep -qE '^Disallow: /$'; then
  echo "❌ FAILED: robots.txt содержит тотальный 'Disallow: /' — закрыт весь сайт"
  exit 1
fi
echo "✅ robots: тотального запрета нет"

check "sitemap.xml" 200 "$(http_status "${FRONTEND_URL}/sitemap.xml")"
SITEMAP=$(curl -s "${FRONTEND_URL}/sitemap.xml")
check_contains "sitemap: xml-декларация" "<?xml" "$SITEMAP"
check_contains "sitemap: urlset" "<urlset" "$SITEMAP"
LOC_COUNT=$(printf '%s' "$SITEMAP" | grep -c "<loc>" || true)
check_eq "sitemap: количество URL" "3" "$LOC_COUNT"
# Каждый адрес из карты должен реально открываться — тянем по пути с
# проверяемого стенда, а не с хоста, зашитого в sitemap.
for LOC in $(printf '%s' "$SITEMAP" | grep -o '<loc>[^<]*</loc>' | sed 's/<[^>]*>//g'); do
  LOC_PATH="/$(printf '%s' "$LOC" | cut -d/ -f4-)"
  check "sitemap: ${LOC_PATH}" 200 "$(http_status "${FRONTEND_URL}${LOC_PATH}")"
done
if command -v xmllint &>/dev/null; then
  if printf '%s' "$SITEMAP" | xmllint --noout - 2>/dev/null; then
    echo "✅ sitemap: валидный XML (xmllint)"
  else
    echo "❌ FAILED: sitemap.xml не проходит xmllint"
    exit 1
  fi
else
  echo "⏭  sitemap: xmllint не установлен, синтаксическая проверка пропущена"
fi

# ── 12. платные лимиты (опционально) ──────────────────────────────────────────
#
# Только для стенда с MONETIZATION_ENABLED=true. На проде флаг выключен, и эти
# проверки там осмысленно провалятся — поэтому секция за отдельным флагом.
# Триальный PRO смоук-юзера даёт лимиты PRO, поэтому проверяем на FREE-аккаунте:
# понизить план через API нельзя, так что используем гостевой аккаунт (гостю
# триал не выдаётся, он остаётся FREE).

if [ "${SMOKE_MONETIZATION:-0}" = "1" ]; then
  echo ""
  echo "── 12. Платные лимиты (MONETIZATION_ENABLED=true) ───────────────────────"

  RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/guest" \
    -H "Content-Type: application/json" \
    -d "{\"displayName\":\"Smoke Free ${TIMESTAMP}\"}")
  FREE_TOKEN=$(echo "$RESP" | jq -r '.accessToken')
  check_not_empty "гостевой (FREE) токен" "$FREE_TOKEN"

  RESP=$(curl -s "${BASE_URL}/api/v1/me/plan" -H "Authorization: Bearer ${FREE_TOKEN}")
  check_eq "гость остаётся на FREE (триал ему не выдаётся)" "FREE" "$(echo "$RESP" | jq -r '.plan')"

  FREE_GROUP=$(curl -s -X POST "${BASE_URL}/api/v1/groups" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${FREE_TOKEN}" \
    -d "{\"title\":\"Smoke Limits ${TIMESTAMP}\",\"tzId\":\"UTC\"}" | jq -r '.id')
  check_not_empty "группа FREE-аккаунта" "$FREE_GROUP"

  # Лимит слотов: FREE.maxSlotsPerMember = 50, 51-й должен дать 402.
  echo "   набиваем 50 слотов…"
  for i in $(seq 0 49); do
    S=$(date -u -d "+$((i + 1)) hours" +"%Y-%m-%dT%H:00:00Z" 2>/dev/null \
      || date -u -v+$((i + 1))H +"%Y-%m-%dT%H:00:00Z")
    E=$(date -u -d "+$((i + 1)) hours 30 minutes" +"%Y-%m-%dT%H:30:00Z" 2>/dev/null \
      || date -u -v+$((i + 1))H -v+30M +"%Y-%m-%dT%H:30:00Z")
    curl -s -o /dev/null -X POST "${BASE_URL}/api/v1/groups/${FREE_GROUP}/availability" \
      -H "Content-Type: application/json" -H "Authorization: Bearer ${FREE_TOKEN}" \
      -d "{\"startsAt\":\"${S}\",\"endsAt\":\"${E}\"}"
  done
  S=$(date -u -d "+100 hours" +"%Y-%m-%dT%H:00:00Z" 2>/dev/null || date -u -v+100H +"%Y-%m-%dT%H:00:00Z")
  E=$(date -u -d "+100 hours 30 minutes" +"%Y-%m-%dT%H:30:00Z" 2>/dev/null || date -u -v+100H -v+30M +"%Y-%m-%dT%H:30:00Z")
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${BASE_URL}/api/v1/groups/${FREE_GROUP}/availability" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${FREE_TOKEN}" \
    -d "{\"startsAt\":\"${S}\",\"endsAt\":\"${E}\"}")
  check "51-й слот заблокирован" 402 "$STATUS"

  # Лимит инвайтов: FREE.invitesPerHour = 3, 4-й должен дать 402.
  for i in 1 2 3; do
    curl -s -o /dev/null -X POST "${BASE_URL}/api/v1/groups/${FREE_GROUP}/invites" \
      -H "Content-Type: application/json" -H "Authorization: Bearer ${FREE_TOKEN}" \
      -d '{"maxUses":5}'
  done
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${BASE_URL}/api/v1/groups/${FREE_GROUP}/invites" \
    -H "Content-Type: application/json" -H "Authorization: Bearer ${FREE_TOKEN}" \
    -d '{"maxUses":5}')
  check "4-й инвайт за час заблокирован" 402 "$STATUS"

  # Лимит участников (FREE.maxMembersPerGroup = 15) в смоук не входит: чтобы
  # его достичь, нужно завести 15 реальных аккаунтов. Покрыт интеграционным
  # тестом MemberLimitEnforcedTest.
  echo "⏭  лимит участников — покрыт MemberLimitEnforcedTest, в смоук не входит"
else
  echo ""
  echo "⏭  12. Платные лимиты пропущены (SMOKE_MONETIZATION!=1)"
fi

# ── done ──────────────────────────────────────────────────────────────────────

echo ""
echo "✅ All checks passed — API ${BASE_URL} / frontend ${FRONTEND_URL}"
echo "   test user: ${EMAIL}"
echo "   group id:  ${GROUP_ID}"
