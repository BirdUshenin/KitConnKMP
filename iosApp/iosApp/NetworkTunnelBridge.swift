import NetworkExtension
import Shared

/// Реальное добавление VPN-конфигурации: сохраняет NETunnelProviderManager (iOS показывает системный запрос
/// «KitConn VPN хочет добавить конфигурации VPN») и запускает туннель из расширения PacketTunnel.
final class NetworkTunnelBridge: NSObject, TunnelBridge {
    static let extensionBundleId = "ru.kitconn.vpn.kmp.PacketTunnel"

    private var manager: NETunnelProviderManager?
    private var statusObserver: NSObjectProtocol?

    func start(
        configJson: String,
        serverLabel: String,
        onState: @escaping (KotlinInt) -> Void,
        onError: @escaping (String) -> Void
    ) {
        NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, error in
            guard let self else { return }
            if let error { return self.report("Не удалось прочитать VPN-конфигурации", error, onState, onError) }

            // Переиспользуем нашу конфигурацию, чтобы при смене сервера не плодить записи в настройках
            let manager = managers?.first {
                ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == Self.extensionBundleId
            } ?? NETunnelProviderManager()

            let proto = NETunnelProviderProtocol()
            proto.providerBundleIdentifier = Self.extensionBundleId
            proto.serverAddress = serverLabel
            // Конфиг Xray уходит в расширение через providerConfiguration
            proto.providerConfiguration = ["config": configJson, "label": serverLabel]
            manager.protocolConfiguration = proto
            manager.localizedDescription = "KitConn VPN"
            manager.isEnabled = true

            // Именно здесь iOS показывает системный запрос на добавление VPN-конфигурации
            manager.saveToPreferences { error in
                if let error { return self.report("VPN-конфигурация не добавлена", error, onState, onError) }
                // После первого сохранения нужно перечитать, иначе connection ещё не привязан к конфигурации
                manager.loadFromPreferences { error in
                    if let error { return self.report("Не удалось загрузить VPN-конфигурацию", error, onState, onError) }
                    self.manager = manager
                    self.observeStatus(of: manager, onState)
                    do {
                        try manager.connection.startVPNTunnel()
                    } catch {
                        self.report("Не удалось запустить туннель", error, onState, onError)
                    }
                }
            }
        }
    }

    func stop() {
        manager?.connection.stopVPNTunnel()
    }

    private func observeStatus(of manager: NETunnelProviderManager, _ onState: @escaping (KotlinInt) -> Void) {
        if let old = statusObserver { NotificationCenter.default.removeObserver(old) }
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange, object: manager.connection, queue: .main
        ) { _ in
            switch manager.connection.status {
            case .connected: onState(2)
            case .connecting, .reasserting: onState(1)
            default: onState(0)
            }
        }
    }

    private func report(
        _ title: String,
        _ error: Error,
        _ onState: @escaping (KotlinInt) -> Void,
        _ onError: @escaping (String) -> Void
    ) {
        DispatchQueue.main.async {
            onState(0)
            onError("\(title): \(error.localizedDescription)")
        }
    }
}
