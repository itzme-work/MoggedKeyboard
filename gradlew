#!/bin/sh
# ─────────────────────────────────────────────────────────────
#  Gradle для тех, у кого он не установлен.
#
#  Тот же дистрибутив и та же сумма, что в CI (check.yml, build.yml):
#  без этого сборка «у меня работает, а в CI нет» — обычное дело.
#  На телефоне не нужен: там JDK и Android SDK нет, собирает только CI.
#
#  Запуск:
#    ./gradlew testReleaseUnitTest lintRelease
# ─────────────────────────────────────────────────────────────
set -e

VER=8.7
SHA=544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/mogged-gradle"
GRADLE="$CACHE/gradle-$VER/bin/gradle"

# Установленный Gradle 8.7 — берём его.
if [ ! -x "$GRADLE" ] && command -v gradle >/dev/null 2>&1; then
  if gradle --version 2>/dev/null | grep -q "Gradle $VER"; then
    exec gradle "$@"
  fi
fi

if [ ! -x "$GRADLE" ]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/gradle-$VER-bin.zip"
  echo "→ Скачиваю Gradle $VER"
  rm -f "$ZIP"
  OK=0
  for URL in \
    "https://services.gradle.org/distributions/gradle-$VER-bin.zip" \
    "https://mirrors.cloud.tencent.com/gradle/gradle-$VER-bin.zip" \
    "https://repo.huaweicloud.com/gradle/gradle-$VER-bin.zip"
  do
    if curl -fsSL --retry 3 --retry-all-errors --retry-delay 5 -o "$ZIP" "$URL"; then
      OK=1
      break
    fi
  done
  if [ "$OK" -ne 1 ]; then
    echo "Не удалось скачать Gradle $VER" >&2
    exit 1
  fi
  echo "$SHA  $ZIP" | sha256sum -c - >/dev/null
  unzip -q -o "$ZIP" -d "$CACHE"
  rm -f "$ZIP"
fi

exec "$GRADLE" "$@"
