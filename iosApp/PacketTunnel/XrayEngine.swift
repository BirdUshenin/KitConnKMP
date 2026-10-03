import NetworkExtension

/// Шов между туннелем и нативными библиотеками. Расширению нужны две вещи:
///  1. ядро Xray (libXray) — слушает локальный SOCKS 127.0.0.1:10808 и ходит к серверу по VLESS;
///  2. мост TUN→SOCKS (HevSocks5Tunnel) — берёт пакеты из packetFlow и отдаёт их в этот SOCKS.
/// Пока библиотеки не добавлены, используется MissingEngine: туннель честно сообщает, чего не хватает.
protocol XrayEngine {
    func start(configJSON: String, packetFlow: NEPacketTunnelFlow) throws
    func stop()
}

struct MissingEngine: XrayEngine {
    func start(configJSON: String, packetFlow: NEPacketTunnelFlow) throws { throw TunnelError.engineMissing }
    func stop() {}
}

enum XrayEngineFactory {
    static func make() -> XrayEngine {
        // TODO: когда libXray и HevSocks5Tunnel подключены, вернуть реализацию на их API
        MissingEngine()
    }
}
