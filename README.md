# KitConn VPN — Kotlin Multiplatform

Общий код для Android и iOS: логика, ViewModel и весь интерфейс (Compose Multiplatform).
Платформенными остаются только VPN-движок и точка входа.

```
shared/       KMP-модуль (commonMain + androidMain + iosMain + jvm для тестов)
  core/         VlessParser, XrayConfigBuilder, TcpPing, CountryCode
  data/         ConfigsApi (Ktor), KeyValueStore
  vpn/          VpnController — интерфейс, который реализует каждая платформа
  presentation/ MainViewModel, MainUiState, MainAction
  ui/           KitConnApp, MainScreen, ручка Off/On, карточки серверов, флаги, сплэш
androidApp/   MainActivity, VpnService + Xray (libv2ray.aar), уведомление
iosApp/       SwiftUI-хост + Xcode-проект (генерируется generate_project.rb)
```

## Секреты
Токен API в git не попадает:
- Android: `androidApp/src/main/kotlin/com/kitconn/android/Secrets.kt` (`object Secrets { const val API_TOKEN }`)
- iOS: `iosApp/iosApp/Secrets.swift` (шаблон — `iosApp/Secrets.example.swift`)

## Команды
```bash
./gradlew :shared:jvmTest                    # тесты общей логики (быстро, без эмулятора)
./gradlew :androidApp:assembleDebug          # Android
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # iOS-фреймворк

# iOS: проект уже сгенерирован; пересоздать (после изменения списка файлов):
cd iosApp
GEM_HOME=$(ls -d /opt/homebrew/Cellar/cocoapods/*/libexec | head -1) \
  /opt/homebrew/opt/ruby/bin/ruby generate_project.rb            # без туннеля (бесплатный Apple ID); --tunnel — с PacketTunnel
xcodebuild -project KitConnIOS.xcodeproj -scheme KitConnIOS -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
```
Или откройте `iosApp/KitConnIOS.xcodeproj` в Xcode. Фаза сборки сама вызывает Gradle.

## Что готово / чего нет
- Готово: общий UI и логика, Android (VPN-сервис, уведомление), iOS-приложение запускается в симуляторе
  с реальным списком серверов.
- Не сделано, Android: виджет на рабочий стол, release-подпись.
- Не сделано, iOS: **сам VPN-туннель**. Нужен `NEPacketTunnelProvider` (Network Extension) с Xray
  (gomobile-сборка LibXray как xcframework) — это требует платной программы Apple Developer
  (entitlement `packet-tunnel-provider`) и реального устройства (в симуляторе туннели не работают).
  Точка подключения: `TunnelBridge` в `shared/src/iosMain` — его реализует Swift-код.
- `allowInsecure` из ссылок игнорируется: сервер конфигов не может отключать проверку TLS.
- applicationId Android — `com.kitconnvpn.kmp`, чтобы ставилось рядом со старым приложением.

## Windows (desktopApp)
Compose Desktop (JVM) поверх общего модуля: тот же интерфейс, трей, один экземпляр. Подключение: Xray как локальный прокси
(`127.0.0.1:10808` SOCKS, `10809` HTTP) + системный прокси в реестре текущего пользователя (права администратора не нужны).
Прежние настройки прокси запоминаются и возвращаются при отключении; при аварийном завершении следующий запуск их чистит.

```bash
./gradlew :desktopApp:run                              # запуск (на macOS тоже работает: прокси через networksetup)
KITCONN_SELFTEST=1 ./gradlew :desktopApp:run           # проверка без окна: серверы → подключение → внешний IP → отключение
desktopApp/scripts/fetch-xray.sh windows               # скачать xray.exe (SHA-256 проверяется)
./gradlew :desktopApp:packageMsi                       # установщик; собирается ТОЛЬКО на Windows
```
Установщик собирает GitHub Actions (`.github/workflows/windows.yml`): нужен секрет репозитория `KITCONN_API_TOKEN`.
Токен для локальной сборки — `desktopApp/src/main/kotlin/com/kitconn/desktop/Secrets.kt` (в `.gitignore`).

Проверено на macOS: подключение, трафик, статистика, отключение, очистка. **Реестр Windows, трей и MSI на Windows не проверялись.**
