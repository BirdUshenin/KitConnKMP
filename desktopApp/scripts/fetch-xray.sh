#!/bin/bash
# Скачивает ядро Xray-core с официальных релизов и проверяет SHA-256. Нужен перед сборкой установщика.
#   scripts/fetch-xray.sh windows   -> appResources/windows-x64/xray.exe
#   scripts/fetch-xray.sh macos     -> appResources/macos-arm64/xray
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=v26.3.27
case "${1:-windows}" in
  windows) ASSET=Xray-windows-64.zip;     SHA=d004c39288ce9ada487c6f398c7c545f7d749e44bdfdd59dbc9f865afba4e1ad; DIR=appResources/windows-x64; BIN=xray.exe ;;
  macos)   ASSET=Xray-macos-arm64-v8a.zip; SHA=2e93a67e8aa1936ecefb307e120830fcbd4c643ab9b1c46a2d0838d5f8409eaf; DIR=appResources/macos-arm64;  BIN=xray ;;
  *) echo "Использование: $0 windows|macos"; exit 1 ;;
esac

mkdir -p "$DIR"
[ -f "$DIR/$BIN" ] && { echo "$DIR/$BIN уже есть"; exit 0; }

TMP=$(mktemp -d)
curl -fsSL -o "$TMP/$ASSET" "https://github.com/XTLS/Xray-core/releases/download/$VERSION/$ASSET"
# sha256sum есть в Git Bash на Windows, shasum — на macOS
if command -v sha256sum >/dev/null; then GOT=$(sha256sum "$TMP/$ASSET" | cut -d' ' -f1); else GOT=$(shasum -a 256 "$TMP/$ASSET" | cut -d' ' -f1); fi
[ "$GOT" = "$SHA" ] || { echo "Хеш не совпал: ожидали $SHA, получили $GOT"; exit 1; }
tar -xf "$TMP/$ASSET" -C "$TMP"
cp "$TMP/$BIN" "$DIR/$BIN"
cp "$TMP/LICENSE" "$DIR/XRAY-LICENSE"
chmod +x "$DIR/$BIN" 2>/dev/null || true
echo "OK: $DIR/$BIN"
