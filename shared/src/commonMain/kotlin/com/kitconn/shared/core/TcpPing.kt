package com.kitconn.shared.core

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Время TCP-подключения до сервера в мс; -1, если не удалось за [timeoutMs]. */
suspend fun tcpPing(host: String, port: Int, timeoutMs: Long = 3000): Int {
    val selector = SelectorManager(Dispatchers.Default)
    return try {
        val start = TimeSource.Monotonic.markNow()
        val ok = withTimeoutOrNull(timeoutMs) {
            val socket = aSocket(selector).tcp().connect(host, port)
            socket.close()
            true
        }
        if (ok == true) start.elapsedNow().inWholeMilliseconds.toInt().coerceAtLeast(1) else -1
    } catch (_: Throwable) {
        -1
    } finally {
        selector.close()
    }
}
