package com.kitconn.desktop

import com.kitconn.shared.presentation.AppDependencies
import com.kitconn.shared.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** `KITCONN_SELFTEST=1`: проходит путь «серверы → подключение → трафик через прокси → отключение» без окна. */
internal fun runSelfTest(deps: AppDependencies, controller: DesktopVpnController) = runBlocking {
    fun log(s: String) { println("SELFTEST $s"); System.out.flush() }

    val errorsJob = launch { controller.errors.collect { log("ОШИБКА: $it") } }
    val configs = deps.api.fetchConfigs()
    val config = configs.first()
    log("серверов: ${configs.size}, берём: ${config.displayName} (${config.subtitle})")

    controller.connect(config)
    log("состояние после connect: ${controller.state.value}")

    if (controller.state.value == VpnState.CONNECTED) {
        val client = HttpClient.newBuilder().proxy(ProxySelector.of(InetSocketAddress("127.0.0.1", 10809)))
            .connectTimeout(Duration.ofSeconds(10)).build()
        val ip = runCatching {
            client.send(HttpRequest.newBuilder(URI("https://api.ipify.org")).timeout(Duration.ofSeconds(12)).build(),
                HttpResponse.BodyHandlers.ofString()).body()
        }.getOrElse { "ошибка: ${it.message}" }
        log("внешний IP через прокси: $ip")
        delay(2500)
        log("трафик: ${controller.traffic.value}")
    }

    controller.disconnect()
    delay(2000)
    log("состояние после disconnect: ${controller.state.value}")
    controller.shutdown()
    errorsJob.cancel()
}
