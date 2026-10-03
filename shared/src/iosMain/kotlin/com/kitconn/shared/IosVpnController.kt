package com.kitconn.shared

import com.kitconn.shared.core.VlessParser
import com.kitconn.shared.core.XrayConfigBuilder
import com.kitconn.shared.core.XrayInbound
import com.kitconn.shared.data.KeyValueStore
import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.vpn.VpnController
import com.kitconn.shared.vpn.VpnState
import com.kitconn.shared.vpn.VpnTraffic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * iOS. Два режима:
 *  1. есть [bridge] (сборка с Network Extension): туннель поднимает наше расширение;
 *  2. моста нет (бесплатный Apple ID): VPN держит сторонний клиент, а мы управляем им через
 *     быстрые команды и следим за состоянием по появлению туннельного интерфейса в системе.
 */
internal class IosVpnController(
    private val bridge: TunnelBridge?,
    private val store: KeyValueStore,
) : VpnController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    private val _traffic = MutableStateFlow(VpnTraffic())
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)

    // Для режима быстрых команд: ждём подтверждения от системы и не отвечаем на старое состояние сразу после команды
    private var connectingSince: TimeSource.Monotonic.ValueTimeMark? = null
    private var ignoreActiveUntil: TimeSource.Monotonic.ValueTimeMark? = null

    override val state: StateFlow<VpnState> = _state.asStateFlow()
    override val traffic: StateFlow<VpnTraffic> = _traffic.asStateFlow()
    override val errors: SharedFlow<String> = _errors

    init {
        if (bridge == null) scope.launch { watchSystemVpn() }
    }

    override suspend fun connect(config: VpnConfig) {
        if (bridge != null) connectViaExtension(bridge, config) else connectViaShortcut(config)
    }

    override fun disconnect() {
        if (bridge != null) {
            bridge.stop()
            _state.value = VpnState.DISCONNECTED
        } else {
            ShortcutLauncher.run(ShortcutLauncher.OFF)
            connectingSince = null
            ignoreActiveUntil = TimeSource.Monotonic.markNow() + 8.seconds
            _state.value = VpnState.DISCONNECTED
        }
    }

    // ───────── режим быстрых команд ─────────

    private fun connectViaShortcut(config: VpnConfig) {
        // Первый раз для сервера передаём ссылку клиенту (он создаёт VPN-конфигурацию), дальше только включаем её
        val importedKey = "imported:${config.config}"
        if (store.getString(importedKey) == null) {
            val result = ExternalClients.handOff(config.config)
            if (result.opened) {
                store.putString(importedKey, "1")
                _errors.tryEmit("${result.message}. Импортируйте сервер в клиенте и создайте быстрые команды «${ShortcutLauncher.ON}» и «${ShortcutLauncher.OFF}» (см. SHORTCUTS.md)")
            } else {
                _errors.tryEmit(result.message)
            }
            return
        }
        if (ShortcutLauncher.run(ShortcutLauncher.ON)) {
            ignoreActiveUntil = null
            connectingSince = TimeSource.Monotonic.markNow()
            _state.value = VpnState.CONNECTING
        } else {
            _errors.tryEmit("Не удалось открыть «Быстрые команды»")
        }
    }

    /** Состояние берём из системы, а не из своих предположений: так таймер и статус честные. */
    private suspend fun watchSystemVpn() {
        while (scope.isActive) {
            val active = isVpnActive()
            val ignoring = ignoreActiveUntil?.hasNotPassedNow() == true
            when {
                active && !ignoring -> {
                    connectingSince = null
                    if (_state.value != VpnState.CONNECTED) _state.value = VpnState.CONNECTED
                }
                !active && _state.value == VpnState.CONNECTED -> _state.value = VpnState.DISCONNECTED
                !active && _state.value == VpnState.CONNECTING -> {
                    // Команда не сработала (нет команды или клиент не подключился)
                    if (connectingSince?.let { it.elapsedNow() > 20.seconds } == true) {
                        connectingSince = null
                        _state.value = VpnState.DISCONNECTED
                        _errors.tryEmit("VPN не включился. Проверьте быстрые команды «${ShortcutLauncher.ON}» и «${ShortcutLauncher.OFF}»")
                    }
                }
            }
            delay(1500)
        }
    }

    // ───────── режим Network Extension ─────────

    private fun connectViaExtension(bridge: TunnelBridge, config: VpnConfig) {
        try {
            // На iOS трафик из туннеля попадает в локальный SOCKS ядра (мост TUN→SOCKS внутри расширения)
            val json = XrayConfigBuilder.build(VlessParser.parse(config.config), XrayInbound.Socks(SOCKS_PORT))
            _state.value = VpnState.CONNECTING
            bridge.start(
                configJson = json,
                serverLabel = config.displayName,
                onState = { code ->
                    _state.value = when (code) {
                        2 -> VpnState.CONNECTED
                        1 -> VpnState.CONNECTING
                        else -> VpnState.DISCONNECTED
                    }
                },
                onError = { message ->
                    _state.value = VpnState.DISCONNECTED
                    _errors.tryEmit(message)
                },
            )
        } catch (e: Exception) {
            _state.value = VpnState.DISCONNECTED
            _errors.tryEmit("Не удалось запустить туннель: ${e.message}")
        }
    }

    private companion object {
        const val SOCKS_PORT = 10808
    }
}
