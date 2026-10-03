package com.kitconn.desktop

import com.kitconn.shared.vpn.VpnTraffic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Запускает xray как дочерний процесс и опрашивает его статистику. */
class XrayProcess(private val binary: File, private val apiPort: Int) {
    private var process: Process? = null
    private val lock = Any()
    private val pidFile = File(AppPaths.dataDir, "xray.pid")

    /** Вызывается, если процесс завершился сам (не из-за [stop]). */
    var onUnexpectedExit: (() -> Unit)? = null

    suspend fun start(configJson: String, socksPort: Int) = withContext(Dispatchers.IO) {
        check(binary.isFile) { "Не найдено ядро Xray: ${binary.absolutePath}" }
        // Упаковщик (jpackage) копирует ресурсы без бита исполнения; на Windows он не нужен, на macOS/Linux нужен
        if (!binary.canExecute()) binary.setExecutable(true)
        stop()

        // В конфиге uuid сервера: доступ только владельцу (на Windows права наследуются от профиля пользователя)
        val config = File(AppPaths.dataDir, "config.json")
        config.writeText(configJson)
        config.setReadable(false, false); config.setReadable(true, true)
        config.setWritable(false, false); config.setWritable(true, true)

        val p = ProcessBuilder(binary.absolutePath, "run", "-c", config.absolutePath)
            .redirectErrorStream(true)
            .start()
        synchronized(lock) { process = p }
        pidFile.writeText(p.pid().toString())

        // Читаем вывод, иначе заполненный буфер канала остановит процесс; хвост пригодится для сообщения об ошибке
        val tail = StringBuilder()
        Thread {
            p.inputStream.bufferedReader().forEachLine { line ->
                synchronized(tail) { tail.append(line).append('\n'); if (tail.length > 600) tail.delete(0, tail.length - 600) }
            }
        }.apply { isDaemon = true }.start()

        Thread {
            p.waitFor()
            val stoppedByUs = synchronized(lock) { process !== p }
            if (!stoppedByUs) onUnexpectedExit?.invoke()
        }.apply { isDaemon = true }.start()

        // Готовность: порт SOCKS начинает принимать соединения
        repeat(40) {
            if (!p.isAlive) return@repeat
            if (portOpen(socksPort)) return@withContext
            delay(150)
        }
        val log = synchronized(tail) { tail.toString().trim() }
        stop()
        error("Xray не запустился. $log")
    }

    fun stop() {
        val p = synchronized(lock) { process.also { process = null } } ?: return
        p.destroy()
        if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly()
        pidFile.delete()
    }

    /** Суммарный трафик через прокси (отправлено, получено) в байтах; null, если запросить не удалось. */
    suspend fun traffic(): VpnTraffic? = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder(binary.absolutePath, "api", "statsquery", "-s", "127.0.0.1:$apiPort")
                .redirectErrorStream(false).start()
            val out = p.inputStream.readBytes().decodeToString()
            p.waitFor(3, TimeUnit.SECONDS)
            parseTraffic(out)
        }.getOrNull()
    }

    companion object {
        internal fun parseTraffic(json: String): VpnTraffic? {
            val stats = (Json.parseToJsonElement(json).jsonObject["stat"] as? JsonArray) ?: return null
            var up = 0L
            var down = 0L
            for (item in stats) {
                val o = item as? JsonObject ?: continue
                val name = o["name"]?.jsonPrimitive?.content ?: continue
                val value = o["value"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                if (name == "outbound>>>proxy>>>traffic>>>uplink") up = value
                if (name == "outbound>>>proxy>>>traffic>>>downlink") down = value
            }
            return VpnTraffic(up, down)
        }

        /** Убивает ядро, оставшееся от прошлого запуска (приложение завершили аварийно). */
        fun killStale() {
            val pidFile = File(AppPaths.dataDir, "xray.pid")
            val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return
            ProcessHandle.of(pid).ifPresent { h ->
                // PID мог перейти другому процессу: убиваем, только если это действительно xray
                val command = h.info().command().orElse("")
                if (command.endsWith("xray") || command.endsWith("xray.exe")) h.destroyForcibly()
            }
            pidFile.delete()
        }

        private fun portOpen(port: Int): Boolean = try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); true }
        } catch (_: Exception) {
            false
        }
    }
}
