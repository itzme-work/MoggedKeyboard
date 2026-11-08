#!/bin/sh
# ─────────────────────────────────────────────────────────────
#  Zip-архив репозитория.
#
#  Локального zip на телефоне нет, поэтому архив собирает сам
#  GitHub (ровно то, что лежит в ветке). Скрипт только скачивает
#  его и кладёт рядом с проектом.
#
#  Запуск:
#    sh tools/make-zip.sh              — версия берётся из build.gradle.kts
#    sh tools/make-zip.sh 0.5.0        — имя архива вручную
# ─────────────────────────────────────────────────────────────
set -e

cd "$(dirname "$0")/.."

USER="${GITHUB_USER:-itzme-work}"
REPO="${REPO:-MoggedKeyboard}"
BRANCH="${BRANCH:-main}"

VER="$1"
if [ -z "$VER" ]; then
  VER=$(sed -n 's/.*appVersionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
fi
VER="${VER:-dev}"

OUT_DIR="/storage/emulated/0/Download"
if [ ! -d "$OUT_DIR" ]; then
  OUT_DIR="$(pwd)"
fi
OUT="$OUT_DIR/$REPO-$VER.zip"
TMP="${TMPDIR:-/data/local/tmp}/mogged-repo.zip"

URL="https://github.com/$USER/$REPO/archive/refs/heads/$BRANCH.zip"

echo "→ Скачиваю архив репозитория $USER/$REPO ($BRANCH)"
rm -f "$TMP"

OK=0
# Сначала напрямую (с рабочими адресами github.com), потом зеркало:
# так быстрее, а зеркало остаётся на случай блокировки.
for IP in 140.82.112.3 140.82.113.3 140.82.116.3 ""; do
  if [ -n "$IP" ]; then
    echo "  пробую $IP"
    curl -fsSL --max-time 45 --resolve "github.com:443:$IP" -o "$TMP" "$URL" || true
  else
    echo "  пробую ghfast.top"
    curl -fsSL --max-time 60 -o "$TMP" "https://ghfast.top/$URL" || true
  fi

  if unzip -l "$TMP" >/dev/null 2>&1; then
    OK=1
    break
  fi
  rm -f "$TMP"
done

if [ "$OK" != "1" ]; then
  echo "Не удалось скачать архив. Проверьте интернет и повторите:"
  echo "  sh tools/make-zip.sh $VER"
  exit 1
fi

cp "$TMP" "$OUT"
rm -f "$TMP"

COUNT=$(unzip -l "$OUT" | tail -1 | awk '{print $2}')
echo ""
echo "Готово: $OUT"
echo "  файлов:  $COUNT"
echo "  размер:  $(wc -c < "$OUT") байт"
echo "  sha256:  $(sha256sum "$OUT" | awk '{print $1}')"
