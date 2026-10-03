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
javaw = dir & "\runtime\bin\javaw.exe"
' Common mistake: running from inside the ZIP. Explorer extracts only this file, without runtime and lib.
' Keep this file ASCII-only: wscript reads it in the ANSI code page and Cyrillic would be garbled.
If Not fso.FileExists(javaw) Then
    MsgBox "KitConn VPN cannot start: the 'runtime' folder was not found next to this file." & vbCrLf & vbCrLf & _
           "You are probably running it from inside the ZIP archive." & vbCrLf & _
           "Right-click the ZIP -> Extract All..., then open KitConn.vbs from the extracted folder.", 48, "KitConn VPN"
    WScript.Quit 1
End If
shell.CurrentDirectory = dir
cmd = """" & javaw & """ -Dcompose.application.resources.dir=""" & dir & "\resources"" -cp """ & dir & "\lib\*"" com.kitconn.desktop.MainKt"
shell.Run cmd, 0, False
VBS
cat > "$OUT/KitConn-debug.bat" <<'BAT'
@echo off
cd /d "%~dp0"
"runtime\bin\java.exe" -Dcompose.application.resources.dir="%~dp0resources" -cp "%~dp0lib\*" com.kitconn.desktop.MainKt
pause
BAT
cat > "$OUT/diagnose.bat" <<'BAT'
@echo off
chcp 65001 >nul
rem Run this WHILE KitConn is connected (On). It writes diagnose-result.txt next to this file.
set OUT=%~dp0diagnose-result.txt
echo === KitConn diagnose === > "%OUT%"
echo --- Windows proxy settings --- >> "%OUT%"
for %%V in (ProxyEnable ProxyServer ProxyOverride AutoConfigURL AutoDetect) do reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v %%V >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- xray.exe process --- >> "%OUT%"
tasklist /FI "IMAGENAME eq xray.exe" >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- listening ports 10808 / 10809 / 10085 --- >> "%OUT%"
netstat -ano | findstr /C:":10808" /C:":10809" /C:":10085" >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- external IP through the KitConn proxy (should be the SERVER ip) --- >> "%OUT%"
curl.exe -s -m 20 -x http://127.0.0.1:10809 https://api.ipify.org >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- external IP direct (your real ip) --- >> "%OUT%"
curl.exe -s -m 20 https://api.ipify.org >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- external IP through the WINDOWS SYSTEM proxy (PowerShell uses it, like browsers do) --- >> "%OUT%"
powershell -NoProfile -Command "try { (Invoke-WebRequest 'https://api.ipify.org' -UseBasicParsing -TimeoutSec 25).Content } catch { 'ERROR: ' + $_.Exception.Message }" >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- system proxy as seen by .NET (should be http://127.0.0.1:10809/) --- >> "%OUT%"
powershell -NoProfile -Command "[System.Net.WebRequest]::GetSystemWebProxy().GetProxy('https://api.ipify.org')" >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- WinHTTP proxy --- >> "%OUT%"
netsh winhttp show proxy >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- connection flags (auto-detect / PAC) --- >> "%OUT%"
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings\Connections" /v DefaultConnectionSettings >> "%OUT%" 2>&1
echo. >> "%OUT%"
echo --- browsers running --- >> "%OUT%"
tasklist /FI "IMAGENAME eq chrome.exe" /FI "STATUS eq running" 2>nul | findstr /I "chrome" >> "%OUT%"
tasklist /FI "IMAGENAME eq msedge.exe" 2>nul | findstr /I "msedge" >> "%OUT%"
tasklist /FI "IMAGENAME eq yandex.exe" 2>nul | findstr /I "yandex" >> "%OUT%"
tasklist /FI "IMAGENAME eq firefox.exe" 2>nul | findstr /I "firefox" >> "%OUT%"
tasklist /FI "IMAGENAME eq opera.exe" 2>nul | findstr /I "opera" >> "%OUT%"
echo. >> "%OUT%"
echo --- KitConn log (last 80 lines) --- >> "%OUT%"
powershell -NoProfile -Command "if (Test-Path \"$env:APPDATA\KitConn\kitconn.log\") { Get-Content -Encoding UTF8 -Tail 80 \"$env:APPDATA\KitConn\kitconn.log\" } else { 'no log file' }" >> "%OUT%" 2>&1
type "%OUT%"
echo.
echo Saved to %OUT% - send this file.
pause
BAT
cat > "$OUT/README.txt" <<'TXT'
KitConn VPN 4.0.0 для Windows (портативная версия)

1. ВАЖНО: распакуйте архив ПОЛНОСТЬЮ: правый клик по zip → «Извлечь всё…» → папка, например C:\KitConn (не в «Program Files»).
   Нельзя запускать файл прямо из окна архива: без соседних папок он не найдёт Java и покажет ошибку.
2. Откройте извлечённую папку и запустите KitConn.vbs двойным щелчком. Окно приложения появится, значок кота — в трее (возле часов).
3. Нажмите ручку On, чтобы подключиться. Закрытие окна сворачивает приложение в трей; выход: правый клик по значку → «Выйти».
4. Ярлык на рабочий стол: правый клик по KitConn.vbs → «Отправить» → «Рабочий стол (создать ярлык)».

Если Windows показывает «Защитник Windows SmartScreen»: «Подробнее» → «Выполнить в любом случае».
Антивирус может реагировать на xray.exe (ядро VLESS) — добавьте папку в исключения.
Если подключение есть, а трафик не идёт: при включённом On запустите diagnose.bat и пришлите diagnose-result.txt.
Если приложение не запускается, откройте KitConn-debug.bat и пришлите текст из окна.
TXT

rm -f "$ZIP"
(cd build/portable && zip -qr "../KitConn-$VERSION-windows-portable.zip" KitConn)
echo "Готово: $ZIP ($(du -h "$ZIP" | cut -f1))"
