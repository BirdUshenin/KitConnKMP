import SwiftUI
import Shared

/// Хост для общего Compose-интерфейса из модуля :shared.
struct ComposeView: UIViewControllerRepresentable {
    // Мост живёт всё время работы приложения: он наблюдает за статусом туннеля
    private static let tunnel = NetworkTunnelBridge()

    private static var tunnelBridge: TunnelBridge? {
        #if TUNNEL
        return tunnel
        #else
        return nil
        #endif
    }

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            apiToken: Secrets.apiToken,
            versionName: "4.0.0",
            appVersion: 4,
            // Без флага TUNNEL (сборка без --tunnel) моста нет, и приложение передаёт ссылку внешнему клиенту
            tunnel: Self.tunnelBridge
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .background(Color(red: 0.047, green: 0.051, blue: 0.078))
    }
}
