package com.kitconn.desktop

import com.kitconn.shared.core.VlessParser
import com.kitconn.shared.core.XrayConfigBuilder
import com.kitconn.shared.core.XrayInbound
import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.vpn.VpnController
import com.kitconn.shared.vpn.VpnState
import com.kitconn.shared.vpn.VpnTraffic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Десктоп: Xray как локальный прокси + системный прокси на него (тот же подход, что в macOS-приложении). */
class DesktopVpnController(
    private val xray: XrayProcess,
    private val proxy: SystemProxy,
    private val ports: XrayInbound.LocalProxy = XrayInbound.LocalProxy(),
) : VpnController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    private val _traffic = MutableStateFlow(VpnTraffic())
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var statsJob: Job? = null

    override val state: StateFlow<VpnState> = _state.asStateFlow()
    override val traffic: StateFlow<VpnTraffic> = _traffic.asStateFlow()
    override val errors: SharedFlow<String> = _errors

    init {
        xray.onUnexpectedExit = {
            if (_state.value != VpnState.DISCONNECTED) {
                _errors.tryEmit("Соединение прервано: ядро Xray остановилось")
                disconnect()
            }
        }
    }

    override suspend fun connect(config: VpnConfig) {
        if (_state.value != VpnState.DISCONNECTED) return
        _state.value = VpnState.CONNECTING
        try {
            val json = XrayConfigBuilder.build(VlessParser.parse(config.config), ports)
            xray.start(json, ports.socksPort)
            // Если за время запуска нажали «Отключить», состояние уже сброшено
            if (_state.value != VpnState.CONNECTING) { xray.stop(); return }
            withContext(Dispatchers.IO) { proxy.enable(ports.httpPort, ports.socksPort) }
            if (_state.value != VpnState.CONNECTING) { cleanup(); return }
            _state.value = VpnState.CONNECTED
            startStats()
        } catch (e: Exception) {
            cleanup()
            _state.value = VpnState.DISCONNECTED
            _errors.tryEmit(e.message ?: e::class.simpleName ?: "Ошибка подключения")
        }
    }

    override fun disconnect() {
        statsJob?.cancel()
        statsJob = null
        _state.value = VpnState.DISCONNECTED
        _traffic.value = VpnTraffic()
        // Прокси снимаем всегда: иначе при остановленном ядре у пользователя пропадёт интернет
        scope.launch(Dispatchers.IO) { cleanup() }
    }

    /** Синхронная очистка для завершения приложения. */
    fun shutdown() {
        statsJob?.cancel()
        cleanup()
    }

    private fun cleanup() {
        xray.stop()
        runCatching { proxy.disable() }
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                xray.traffic()?.let { _traffic.value = it }
            }
        }
    }
}
