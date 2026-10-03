import NetworkExtension
import os.log

/// Расширение, в котором живёт туннель. Получает конфиг Xray от приложения через providerConfiguration,
/// настраивает сетевой интерфейс и запускает ядро (см. XrayEngine).
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let log = Logger(subsystem: "ru.kitconn.vpn.kmp", category: "tunnel")
    private var engine: XrayEngine = XrayEngineFactory.make()

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        guard let proto = protocolConfiguration as? NETunnelProviderProtocol,
              let config = proto.providerConfiguration?["config"] as? String else {
            return completionHandler(TunnelError.noConfig)
        }

        // Параметры интерфейса те же, что на Android
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        let ipv4 = NEIPv4Settings(addresses: ["10.0.0.2"], subnetMasks: ["255.255.255.0"])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4
        let ipv6 = NEIPv6Settings(addresses: ["fd00::1"], networkPrefixLengths: [128])
        ipv6.includedRoutes = [NEIPv6Route.default()]
        settings.ipv6Settings = ipv6
        settings.dnsSettings = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8"])
        settings.mtu = 1500

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self else { return }
            if let error {
                self.log.error("setTunnelNetworkSettings: \(error.localizedDescription)")
                return completionHandler(error)
            }
            do {
                try self.engine.start(configJSON: config, packetFlow: self.packetFlow)
                self.log.info("Xray started")
                completionHandler(nil)
            } catch {
                self.log.error("engine.start: \(error.localizedDescription)")
                completionHandler(error)
            }
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        engine.stop()
        completionHandler()
    }
}

enum TunnelError: LocalizedError {
    case noConfig
    case engineMissing

    var errorDescription: String? {
        switch self {
        case .noConfig: return "В расширение не передан конфиг Xray"
        case .engineMissing:
            return "Ядро Xray не подключено к расширению: добавьте libXray и HevSocks5Tunnel (см. iosApp/PacketTunnel/README.md)"
        }
    }
}
