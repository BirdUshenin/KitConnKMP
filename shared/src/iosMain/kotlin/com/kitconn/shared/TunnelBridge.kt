package com.kitconn.shared

/**
 * Мост к Network Extension на стороне Swift (`NetworkTunnelBridge` в iosApp).
 * Именно он создаёт VPN-конфигурацию (системный запрос «Добавить конфигурации VPN») и запускает туннель.
 */
interface TunnelBridge {
    /**
     * Запускает туннель с конфигом Xray. [onState]: 0 — отключён, 1 — подключение, 2 — подключён.
     * [onError] получает текст для пользователя (например, отказ добавить конфигурацию).
     */
    fun start(configJson: String, serverLabel: String, onState: (Int) -> Unit, onError: (String) -> Unit)
    fun stop()
}
