#!/bin/sh
# ─────────────────────────────────────────────────────────────
#  Одноразовое создание ключа подписи Mogged Keyboard.
#
#  Запускать ТОЛЬКО ОДИН РАЗ. Ключ нельзя пересоздавать: с ним
#  связаны все будущие обновления приложения.
#
#  Нужен keytool (Java) ИЛИ openssl — что найдётся:
#    pkg install openjdk-17
#    pkg install openssl
#
#  Запуск:
#    sh tools/make-keystore.sh
#
#  Скрипт создаст mogged.jks и mogged.b64 рядом с собой.
#  mogged.b64 — это содержимое секрета KEYSTORE_BASE64.
#  Файлы нельзя коммитить в репозиторий и нельзя терять.
# ─────────────────────────────────────────────────────────────
set -e

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT="$DIR/mogged.jks"
B64="$DIR/mogged.b64"

if [ -e "$OUT" ]; then
  echo "Ключ уже существует: $OUT"
  echo ""
  echo "Новый ключ создавать НЕЛЬЗЯ — иначе обновления перестанут"
  echo "ставиться поверх установленного приложения."
  echo ""
  echo "Если нужен base64 для секрета:"
  echo "  base64 \"$OUT\" | tr -d '\\n'"
  exit 1
fi

printf 'Пароль ключа (не короче 12 символов): '
stty -echo 2>/dev/null || true
read -r PASS1
stty echo 2>/dev/null || true
printf '\nПовторите пароль: '
stty -echo 2>/dev/null || true
read -r PASS2
stty echo 2>/dev/null || true
printf '\n'

if [ "$PASS1" != "$PASS2" ]; then
  echo "Пароли не совпали."
  exit 1
fi

if [ ${#PASS1} -lt 12 ]; then
  echo "Слишком короткий пароль: нужно минимум 12 символов."
  exit 1
fi

if command -v keytool >/dev/null 2>&1; then
  echo "→ Создаю ключ через keytool"

  keytool -genkeypair \
    -keystore "$OUT" \
    -storetype PKCS12 \
    -alias mogged \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$PASS1" \
    -keypass "$PASS1" \
    -dname "CN=Mogged Keyboard, O=Mogged Keyboard, C=RU"

elif command -v openssl >/dev/null 2>&1; then
  echo "→ keytool не найден, создаю ключ через openssl"

  TMPKEY="$DIR/.mogged-key.pem"
  TMPCRT="$DIR/.mogged-cert.pem"
  rm -f "$TMPKEY" "$TMPCRT"

  openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -nodes \
    -keyout "$TMPKEY" -out "$TMPCRT" \
    -subj "/CN=Mogged Keyboard/O=Mogged Keyboard/C=RU" >/dev/null 2>&1

  chmod 600 "$TMPKEY" "$TMPCRT"

  # Android и Gradle читают PKCS12 без keytool.
  openssl pkcs12 -export \
    -name mogged \
    -inkey "$TMPKEY" \
    -in "$TMPCRT" \
    -out "$OUT" \
    -passout "pass:$PASS1"

  rm -f "$TMPKEY" "$TMPCRT"

else
  echo "Не найдено ни keytool, ни openssl."
  echo ""
  echo "Поставьте одно из двух:"
  echo "  pkg update && pkg install openjdk-17"
  echo "  pkg install openssl"
  exit 1
fi

chmod 600 "$OUT"
base64 "$OUT" | tr -d '\n' > "$B64"
chmod 600 "$B64"

echo ""
echo "Готово."
echo "  ключ:   $OUT"
echo "  base64: $B64"
echo ""
echo "Теперь добавьте два секрета в GitHub:"
echo "  github.com/itzme-work/MoggedKeyboard/settings/secrets/actions/new"
echo ""
echo "  KEYSTORE_PASSWORD — пароль, который вы ввели"
echo "  KEYSTORE_BASE64   — всё содержимое файла:"
echo "                      $B64"
echo ""
echo "Скопировать base64 в буфер обмена:"
echo "  termux-clipboard-set < \"$B64\""
echo ""
echo "ВАЖНО: сделайте резервную копию $OUT."
echo "Если ключ потерян, обновления больше не установятся —"
echo "пользователям придётся удалить приложение и поставить заново."
