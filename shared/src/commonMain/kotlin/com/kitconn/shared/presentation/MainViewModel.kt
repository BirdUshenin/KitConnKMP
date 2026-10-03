package com.kitconn.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitconn.shared.core.VlessParser
import com.kitconn.shared.core.tcpPing
import com.kitconn.shared.data.ConfigsApi
import com.kitconn.shared.data.KeyValueStore
import com.kitconn.shared.data.UpdateRequiredException
import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.vpn.VpnController
import com.kitconn.shared.vpn.VpnState
import com.kitconn.shared.vpn.VpnTraffic
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** Всё, что нужно общему коду от платформы. */
class AppDependencies(
    val vpn: VpnController,
    val store: KeyValueStore,
    val api: ConfigsApi,
    val versionName: String,
)

data class MainUiState(
    val versionName: String = "",
    val isLoading: Boolean = true,
    val updateRequired: Boolean = false,
    val vpnState: VpnState = VpnState.DISCONNECTED,
    val durationSeconds: Long = 0,
    val downBytesPerSec: Double = 0.0,
    val upBytesPerSec: Double = 0.0,
    val configs: List<VpnConfig> = emptyList(),
    val selected: VpnConfig? = null,
    val pings: Map<String, Int> = emptyMap(),   // -1 — сервер не ответил
    val isPinging: Boolean = false,
    val showServers: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface MainAction {
    data object ToggleVpn : MainAction
    data class SelectConfig(val config: VpnConfig) : MainAction
    data object Refresh : MainAction
    data object PingAll : MainAction
    data class SetServersVisible(val visible: Boolean) : MainAction
    data object DismissError : MainAction
}

class MainViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState(versionName = deps.versionName))
    val uiState: StateFlow<MainUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    init {
        observeVpn()
        loadConfigs()
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.ToggleVpn -> toggle()
            is MainAction.SelectConfig -> select(action.config)
            MainAction.Refresh -> loadConfigs()
            MainAction.PingAll -> pingAll()
            is MainAction.SetServersVisible -> _state.update { it.copy(showServers = action.visible) }
            MainAction.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeVpn() {
        viewModelScope.launch {
            deps.vpn.state.collect { vpnState ->
                _state.update { it.copy(vpnState = vpnState) }
                if (vpnState == VpnState.CONNECTED) startTicker() else stopTicker()
            }
        }
        viewModelScope.launch {
            deps.vpn.errors.collect { message -> _state.update { it.copy(errorMessage = message) } }
        }
    }

    /** Раз в секунду: таймер подключения и скорость как разница суммарного трафика. */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var last: VpnTraffic = deps.vpn.traffic.value
            var seconds = 0L
            while (true) {
                delay(1000)
                seconds++
                val now = deps.vpn.traffic.value
                _state.update {
                    it.copy(
                        durationSeconds = seconds,
                        upBytesPerSec = (now.uplinkBytes - last.uplinkBytes).coerceAtLeast(0).toDouble(),
                        downBytesPerSec = (now.downlinkBytes - last.downlinkBytes).coerceAtLeast(0).toDouble(),
                    )
                }
                last = now
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        _state.update { it.copy(durationSeconds = 0, upBytesPerSec = 0.0, downBytesPerSec = 0.0) }
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val started = TimeSource.Monotonic.markNow()
            try {
                val list = deps.api.fetchConfigs()
                _state.update { it.copy(updateRequired = false, configs = list, selected = restoreSelection(list, it.selected)) }
            } catch (_: UpdateRequiredException) {
                _state.update { it.copy(updateRequired = true) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Ошибка загрузки серверов: ${e.message ?: e::class.simpleName}") }
            }
            // Сплэш показываем не меньше минимума, чтобы кот успел подмигнуть
            val minSplashMs = 1600L
            val left = minSplashMs - started.elapsedNow().inWholeMilliseconds
            if (left > 0) delay(left)
            _state.update { it.copy(isLoading = false) }
            if (_state.value.configs.isNotEmpty() && _state.value.pings.isEmpty()) pingAll()
        }
    }

    /** Текущий выбор, затем сохранённый (по ссылке, потом по имени), затем первый сервер. */
    private fun restoreSelection(list: List<VpnConfig>, current: VpnConfig?): VpnConfig? {
        if (list.isEmpty()) return null
        current?.let { cur -> list.firstOrNull { it.config == cur.config }?.let { return it } }
        val url = deps.store.getString(KEY_URL)
        val name = deps.store.getString(KEY_NAME)
        return list.firstOrNull { it.config == url }
            ?: list.firstOrNull { name != null && it.name == name }
            ?: list.first()
    }

    private fun select(config: VpnConfig) {
        val wasOn = _state.value.vpnState != VpnState.DISCONNECTED
        deps.store.putString(KEY_URL, config.config)
        deps.store.putString(KEY_NAME, config.name)
        _state.update { it.copy(selected = config, showServers = false) }
        if (wasOn) {
            deps.vpn.disconnect()
            viewModelScope.launch {
                delay(300)
                deps.vpn.connect(config)
            }
        }
    }

    private fun toggle() {
        when (_state.value.vpnState) {
            VpnState.DISCONNECTED -> {
                val config = _state.value.selected
                if (config == null) {
                    _state.update { it.copy(errorMessage = "Серверы не загружены") }
                    loadConfigs()
                } else {
                    _state.update { it.copy(errorMessage = null) }
                    viewModelScope.launch { deps.vpn.connect(config) }
                }
            }
            VpnState.CONNECTING, VpnState.CONNECTED -> deps.vpn.disconnect()
        }
    }

    private fun pingAll() {
        if (_state.value.isPinging) return
        val configs = _state.value.configs
        if (configs.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isPinging = true, pings = emptyMap()) }
            configs.map { cfg ->
                async {
                    val parsed = runCatching { VlessParser.parse(cfg.config) }.getOrNull()
                    val ms = if (parsed != null) tcpPing(parsed.address, parsed.port) else -1
                    _state.update { it.copy(pings = it.pings + (cfg.config to ms)) }
                }
            }.awaitAll()
            _state.update { it.copy(isPinging = false) }
        }
    }

    private companion object {
        const val KEY_URL = "selected_config_url"
        const val KEY_NAME = "selected_config_name"
    }
}
