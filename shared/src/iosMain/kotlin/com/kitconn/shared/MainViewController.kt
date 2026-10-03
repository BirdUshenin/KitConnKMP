package com.kitconn.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.kitconn.shared.data.ConfigsApi
import com.kitconn.shared.presentation.AppDependencies
import com.kitconn.shared.ui.KitConnApp
import platform.UIKit.UIViewController

/** Точка входа для Swift: `MainViewControllerKt.MainViewController(...)`. */
fun MainViewController(
    apiToken: String,
    versionName: String,
    appVersion: Int,
    tunnel: TunnelBridge?,
): UIViewController {
    val store = UserDefaultsStore()
    val deps = AppDependencies(
        vpn = IosVpnController(tunnel, store),
        store = store,

        api = ConfigsApi("https://kitconn-api.ilyaushenin.ru/", apiToken, appVersion),
        versionName = versionName,
    )
    return ComposeUIViewController { KitConnApp(deps) }
}
