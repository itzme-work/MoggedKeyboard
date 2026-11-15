#!/bin/sh
# ─────────────────────────────────────────────────────────────
#  Достаёт из GitHub ошибки сборки текстом: скачивает лог
#  последнего упавшего запуска и печатает только строки об ошибках.
#
#  Запуск:
#    sh tools/fetch-ci-log.sh          # последний упавший «Build APK»
#    sh tools/fetch-ci-log.sh check    # последний упавший «Проверки»
#
#  Нужен тот же токен, что и для заливки (~/.mogged-token).
# ─────────────────────────────────────────────────────────────
set -e

REPO="${REPO:-MoggedKeyboard}"
TOKEN_FILE="${MOGGED_TOKEN_FILE:-$HOME/.mogged-token}"
USER_FILE="${MOGGED_USER_FILE:-$HOME/.mogged-user}"

TOKEN=$(tr -d ' \r\n' < "$TOKEN_FILE" 2>/dev/null || true)
USER=$(tr -d ' \r\n' < "$USER_FILE" 2>/dev/null || true)
if [ -z "$TOKEN" ] || [ -z "$USER" ]; then
  echo "Нет токена или логина: $TOKEN_FILE, $USER_FILE"
  exit 1
fi

case "${1:-build}" in
  check) WF="check.yml" ;;
  build) WF="build.yml" ;;
  *)     WF="$1" ;;
esac

TMP="${TMPDIR:-/data/local/tmp}"
mkdir -p "$TMP"
H_AUTH="Authorization: Bearer $TOKEN"
H_ACC="Accept: application/vnd.github+json"
API="https://api.github.com"
C="curl -sS --http1.1 --retry 5 --retry-delay 2 --retry-all-errors --connect-timeout 20"

# status=failure отбирает на стороне GitHub; первый «id» в ответе — самый свежий
RUN_ID=$($C "$API/repos/$USER/$REPO/actions/workflows/$WF/runs?status=failure&per_page=3" \
  -H "$H_AUTH" -H "$H_ACC" \
  | grep -o '"id": *[0-9]*' | head -1 | grep -o '[0-9]*')
if [ -z "$RUN_ID" ]; then
  echo "Упавших запусков нет."
  exit 0
fi
echo "→ Запуск $RUN_ID"

jobs="$TMP/mogged-jobs.json"
$C -o "$jobs" "$API/repos/$USER/$REPO/actions/runs/$RUN_ID/jobs" -H "$H_AUTH" -H "$H_ACC"

for jid in $(grep -o '"id": *[0-9]*' "$jobs" | grep -o '[0-9]*'); do
  out="$TMP/mogged-log-$jid.zip"
  code=$($C -o "$out" -w '%{http_code}' \
    "$API/repos/$USER/$REPO/actions/jobs/$jid/logs" -H "$H_AUTH" -H "$H_ACC")
  [ "$code" = "200" ] || continue

  dir="$TMP/mogged-log-$jid"
  rm -rf "$dir"
  unzip -q -o "$out" -d "$dir" 2>/dev/null || continue

  echo ""
  echo "── работа $jid ──"
  # Kotlin печатает «e: file:line:col …», Gradle — «What went wrong» и задачу
  grep -h -E '(^e: |error: |What went wrong|Execution failed for task|> Task .*FAILED)' \
    $(find "$dir" -type f) 2>/dev/null \
    | grep -v -E 'warning:|Deprecat' | head -40
done
