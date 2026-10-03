package com.kitconn.shared.vpn

import com.kitconn.shared.model.VpnConfig
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED }

/** Суммарный трафик с начала подключения, байты. */
data class VpnTraffic(val uplinkBytes: Long = 0, val downlinkBytes: Long = 0)

/**
 * Платформенная часть VPN. Общий код ничего не знает ни про VpnService, ни про Network Extension:
 * на Android реализация поднимает VpnService с Xray, на iOS — NEPacketTunnelProvider.
 */
interface VpnController {
    val state: StateFlow<VpnState>
    val traffic: StateFlow<VpnTraffic>

    /** Человекочитаемые ошибки подключения (отказ в разрешении, падение ядра и т. п.). */
    val errors: SharedFlow<String>

    /** Запускает подключение; состояние меняется через [state]. */
    suspend fun connect(config: VpnConfig)

    fun disconnect()
}
