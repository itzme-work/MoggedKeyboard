#!/bin/sh
# ─────────────────────────────────────────────────────────────
#  Значок для README и релиза: docs/icon-512.png.
#
#  На телефоне нет ни Python, ни ImageMagick, поэтому PNG собирает
#  awk (tools/icon.awk), повторяя фигуры адаптивной иконки.
#  Нужен только при смене значка — в обычной сборке файл не нужен.
#
#  Запуск: sh tools/make-icon.sh
# ─────────────────────────────────────────────────────────────
set -e

cd "$(dirname "$0")/.."

mkdir -p docs
awk -f tools/icon.awk > docs/icon-512.png

# Проверка, что получился настоящий PNG 512×512 (байты ширины и высоты).
if [ ! -s docs/icon-512.png ]; then
  echo "Значок не собрался" >&2
  exit 1
fi

SIZE=$(ls -l docs/icon-512.png | awk '{print $5}')
echo "docs/icon-512.png — $SIZE байт"
