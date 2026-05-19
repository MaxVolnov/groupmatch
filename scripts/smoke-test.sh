#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-https://groupmatch-production.up.railway.app}"
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

# ── done ──────────────────────────────────────────────────────────────────────

echo ""
echo "✅ All checks passed — ${BASE_URL}"
echo "   test user: ${EMAIL}"
echo "   group id:  ${GROUP_ID}"
