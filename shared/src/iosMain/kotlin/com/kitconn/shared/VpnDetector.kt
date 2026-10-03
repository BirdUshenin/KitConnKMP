package com.kitconn.shared

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CFNetwork.CFNetworkCopySystemProxySettings
import platform.Foundation.CFBridgingRelease

/**
 * Определяет, активен ли сейчас какой-либо VPN в системе. Работает и для туннелей других приложений:
 * при подключённом VPN в системных настройках прокси появляется интерфейс tun/utun/ppp/ipsec.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun isVpnActive(): Boolean {
    val settings = CFBridgingRelease(CFNetworkCopySystemProxySettings()) as? Map<*, *> ?: return false
    val scoped = settings["__SCOPED__"] as? Map<*, *> ?: return false
    return scoped.keys.any { key ->
        val name = key.toString().lowercase()
        listOf("tun", "tap", "ppp", "ipsec").any { name.contains(it) }
    }
}
