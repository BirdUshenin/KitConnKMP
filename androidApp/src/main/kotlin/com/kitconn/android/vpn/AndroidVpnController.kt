package com.kitconn.android.vpn

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.kitconn.shared.core.CountryCode
import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.vpn.VpnController
import com.kitconn.shared.vpn.VpnState
import com.kitconn.shared.vpn.VpnTraffic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidVpnController(private val context: Context) : VpnController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _traffic = MutableStateFlow(VpnTraffic())
    private var trafficJob: Job? = null

    /** Запрашивает у пользователя разрешение VPN; задаётся активити, возвращает true при согласии. */
    var permissionRequester: (suspend (Intent) -> Boolean)? = null

    override val state: StateFlow<VpnState> = VpnBridge.state
    override val traffic: StateFlow<VpnTraffic> = _traffic.asStateFlow()
    override val errors: SharedFlow<String> = VpnBridge.errors

    init {
        scope.launch {
            VpnBridge.state.collect { if (it == VpnState.CONNECTED) startTrafficPolling() else stopTrafficPolling() }
        }
    }

    override suspend fun connect(config: VpnConfig) {
        if (state.value != VpnState.DISCONNECTED) return

        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent != null) {
            // Пока пользователь не ответил на системный диалог, состояние остаётся DISCONNECTED
            val granted = permissionRequester?.invoke(permissionIntent) ?: false
            if (!granted) {
                VpnBridge.errors.tryEmit("Разрешение на VPN не выдано")
                return
            }
        }

        val label = listOf(CountryCode.flagEmoji(config.country), config.displayName, config.subtitle)
            .filter { it.isNotEmpty() }.joinToString(" ")
        val intent = Intent(context, KitConnVpnService::class.java)
            .putExtra(KitConnVpnService.EXTRA_VLESS_URL, config.config)
            .putExtra(KitConnVpnService.EXTRA_SERVER_LABEL, label)
        // Сервис становится foreground (уведомление в статус-баре) сразу при старте
        ContextCompat.startForegroundService(context, intent)
    }

    override fun disconnect() {
        context.startService(
            Intent(context, KitConnVpnService::class.java).setAction(KitConnVpnService.ACTION_STOP),
        )
    }

    // Скорость берём из системных счётчиков, как в прежнем приложении
    private fun startTrafficPolling() {
        trafficJob?.cancel()
        val baseRx = TrafficStats.getTotalRxBytes()
        val baseTx = TrafficStats.getTotalTxBytes()
        trafficJob = scope.launch {
            while (isActive) {
                delay(500)
                _traffic.value = VpnTraffic(
                    uplinkBytes = (TrafficStats.getTotalTxBytes() - baseTx).coerceAtLeast(0),
                    downlinkBytes = (TrafficStats.getTotalRxBytes() - baseRx).coerceAtLeast(0),
                )
            }
        }
    }

    private fun stopTrafficPolling() {
        trafficJob?.cancel()
        trafficJob = null
        _traffic.value = VpnTraffic()
    }
}
