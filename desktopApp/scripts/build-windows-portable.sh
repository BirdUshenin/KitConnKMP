#!/bin/bash
# Собирает портативный архив для Windows прямо на macOS/Linux: приложение + Java (jlink из Windows JDK) + xray.exe.
# Нужно заранее: desktopApp/build/portable-tools/jdk-win/<jdk>/jmods (Windows Temurin 21) и appResources/windows-x64/xray.exe.
#   scripts/build-windows-portable.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=4.0.0
OUT=build/portable/KitConn
ZIP=build/KitConn-$VERSION-windows-portable.zip
JMODS=$(ls -d build/portable-tools/jdk-win/*/jmods | head -1)

[ -f appResources/windows-x64/xray.exe ] || { echo "Нет appResources/windows-x64/xray.exe (scripts/fetch-xray.sh windows)"; exit 1; }
[ -d "$JMODS" ] || { echo "Нет jmods Windows JDK: $JMODS"; exit 1; }

rm -rf build/portable && mkdir -p build/portable
(cd .. && ./gradlew :desktopApp:stageWindowsLib -q)
: "${JAVA_HOME:?Укажите JAVA_HOME (JDK 21) для jlink}"

# Модули Java: список берётся из `gradlew :desktopApp:suggestRuntimeModules` (java.instrument, java.management,
# java.net.http, jdk.unsupported) + то, что Compose Desktop и TLS требуют всегда (UI, сеть, JNDI для OkHttp, кривые TLS)
MODULES="java.base,java.desktop,java.logging,java.prefs,java.xml,java.sql,java.instrument,java.management,java.net.http,java.naming,jdk.unsupported,jdk.crypto.ec"
echo "Модули: $MODULES"

# jlink с jmods Windows JDK даёт Windows-окружение, хотя запускается на macOS
"$JAVA_HOME/bin/jlink" --module-path "$JMODS" --add-modules "$MODULES" \
  --strip-debug --no-header-files --no-man-pages --compress zip-6 --output "$OUT/runtime"

mkdir -p "$OUT/resources"
cp appResources/windows-x64/xray.exe appResources/windows-x64/XRAY-LICENSE "$OUT/resources/"
cp icons/icon.ico "$OUT/KitConn.ico"

# Запуск без окна консоли: javaw + VBS; .bat — запасной вариант с консолью для диагностики
cat > "$OUT/KitConn.vbs" <<'VBS'
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = dir
cmd = """" & dir & "\runtime\bin\javaw.exe"" -Dcompose.application.resources.dir=""" & dir & "\resources"" -cp """ & dir & "\lib\*"" com.kitconn.desktop.MainKt"
shell.Run cmd, 0, False
VBS
cat > "$OUT/KitConn-debug.bat" <<'BAT'
@echo off
cd /d "%~dp0"
"runtime\bin\java.exe" -Dcompose.application.resources.dir="%~dp0resources" -cp "%~dp0lib\*" com.kitconn.desktop.MainKt
pause
BAT
cat > "$OUT/README.txt" <<'TXT'
KitConn VPN 4.0.0 для Windows (портативная версия)

1. Распакуйте архив в любую папку (не в «Program Files»), например C:\KitConn.
2. Запустите KitConn.vbs двойным щелчком. Окно приложения появится, значок кота — в трее (возле часов).
3. Нажмите ручку On, чтобы подключиться. Закрытие окна сворачивает приложение в трей; выход: правый клик по значку → «Выйти».
4. Ярлык на рабочий стол: правый клик по KitConn.vbs → «Отправить» → «Рабочий стол (создать ярлык)».

Если Windows показывает «Защитник Windows SmartScreen»: «Подробнее» → «Выполнить в любом случае».
Антивирус может реагировать на xray.exe (ядро VLESS) — добавьте папку в исключения.
Если приложение не запускается, откройте KitConn-debug.bat и пришлите текст из окна.
TXT

rm -f "$ZIP"
(cd build/portable && zip -qr "../KitConn-$VERSION-windows-portable.zip" KitConn)
echo "Готово: $ZIP ($(du -h "$ZIP" | cut -f1))"
