# PacketTunnel — расширение туннеля

Что уже есть: `NEPacketTunnelProvider` принимает конфиг Xray, настраивает интерфейс (адреса, маршруты, DNS, MTU),
вызывает `XrayEngine`. Что осталось — реализация `XrayEngine` на нативных библиотеках:

1. **libXray** (XTLS/libXray, релиз `libxray-apple-cgo.zip`) — запуск Xray с конфигом (SOCKS-вход на 127.0.0.1:10808).
2. **HevSocks5Tunnel** (Tun2SocksKit, `HevSocks5Tunnel.xcframework`) — перекладывает пакеты `packetFlow` в SOCKS.

Положить xcframework в `iosApp/Frameworks`, подключить к таргету `PacketTunnel` (Embed & Sign не нужен — статические),
и заменить `MissingEngine` в `XrayEngineFactory`. Подписка на capability Network Extensions требует платного аккаунта.
