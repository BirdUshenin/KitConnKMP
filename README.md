<p align="center">
  <img src="docs/images/banner.png" alt="KitConn VPN" width="100%">
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-4.0.0-00E5FF?style=for-the-badge&labelColor=0C0D14">
  <img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=0C0D14">
  <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=0C0D14">
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="iOS" src="https://img.shields.io/badge/iOS-000000?style=flat-square&logo=apple&logoColor=white">
  <img alt="macOS" src="https://img.shields.io/badge/macOS-000000?style=flat-square&logo=apple&logoColor=white">
  <img alt="Windows" src="https://img.shields.io/badge/Windows-0078D4?style=flat-square&logo=windows&logoColor=white">
</p>

## О проекте

**KitConn VPN** — клиент для подключения к серверам по протоколу VLESS (Xray-core). Интерфейс и вся логика написаны один раз
на Kotlin Multiplatform и Compose Multiplatform, поэтому приложение выглядит и работает одинаково на Android, iOS, macOS и Windows.

<p align="center">
  <img src="docs/images/screens.png" alt="Скриншоты KitConn VPN" width="100%">
</p>

## Возможности

- 🎛 **Ручка Off / On** с анимацией: нажимается и поворачивается
- ⏱ Таймер подключения, скорость загрузки и отдачи в реальном времени
- 🌍 Список серверов в виде стеклянных карточек, круглые флаги стран и замер пинга
- 💾 Выбранный сервер запоминается
- 🔗 Поддержка конфигураций VLESS: TCP, Reality, TLS, XHTTP
- 🐱 Минималистичный дизайн и сплэш с подмигивающим котом

## Платформы

| Платформа | Как работает VPN | Состояние |
|---|---|---|
| **Android** | `VpnService` + Xray, весь трафик устройства | собирается, уведомление с таймером; проверка на устройстве впереди |
| **macOS** | Xray + системный прокси, приложение в строке меню | работает |
| **Windows** | Xray + системный прокси (реестр), иконка в трее | собрано, ожидает проверки на Windows |
| **iOS** | управляет VLESS-клиентом через «Быстрые команды»; собственный туннель требует Network Extension | интерфейс работает, туннель — в разработке |

> На iOS собственный VPN-туннель возможен только с платной программой Apple Developer (Network Extension).
> Пока приложение передаёт сервер в установленный VLESS-клиент и включает его быстрой командой — см. [`iosApp/SHORTCUTS.md`](iosApp/SHORTCUTS.md).

## Скачать

Готовые сборки — в разделе [Releases](../../releases) (если релиз ещё не опубликован, собрать можно самостоятельно, см. ниже).

- **Windows** — портативный архив `KitConn-4.0.0-windows-portable.zip`: распаковать и запустить `KitConn.vbs`.
- **macOS** — образ `KitConn-4.0.0.dmg`: перетащить в «Программы», при первом запуске «Открыть» правой кнопкой.
- **Android** — пока собирается из исходников (`./gradlew :androidApp:assembleDebug`).

## Структура проекта

```
shared/       Kotlin Multiplatform: логика, ViewModel и весь интерфейс (Compose Multiplatform)
androidApp/   Android: VpnService + Xray, уведомление
iosApp/       iOS: SwiftUI-хост и Xcode-проект
desktopApp/   Windows и macOS (JVM): Xray + системный прокси, трей
```

Подробности по сборке, тестам и устройству каждого модуля — в [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Быстрый старт для разработчика

```bash
./gradlew :shared:jvmTest              # тесты общей логики
./gradlew :androidApp:assembleDebug    # Android
./gradlew :desktopApp:run              # Windows / macOS
```

Токен API в репозиторий не коммитится: шаблон — `iosApp/Secrets.example.swift`, для Android и десктопа файл `Secrets.kt` создаётся локально.

## Благодарности

- [Xray-core](https://github.com/XTLS/Xray-core) — ядро для VLESS и Reality
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) и [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
