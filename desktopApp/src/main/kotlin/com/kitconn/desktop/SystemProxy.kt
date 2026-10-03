package com.kitconn.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/** Включает и выключает системный прокси, указывающий на наш Xray. */
interface SystemProxy {
    fun enable(httpPort: Int, socksPort: Int)
    fun disable()

    /** Выключает прокси только если он указывает на наш Xray (чужие настройки пользователя не трогаем). */
    fun disableIfOurs(httpPort: Int, socksPort: Int)

    companion object {
        fun forCurrentOs(): SystemProxy? = when (DesktopOs.current) {
            DesktopOs.WINDOWS -> WindowsSystemProxy()
            DesktopOs.MACOS -> MacSystemProxy()
            DesktopOs.OTHER -> null
        }
    }
}

internal fun exec(vararg command: String, timeoutSec: Long = 10): String {
    val p = ProcessBuilder(*command).redirectErrorStream(true).start()
    val out = p.inputStream.readBytes().decodeToString()
    if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) p.destroyForcibly()
    check(p.exitValue() == 0) { out.trim().ifEmpty { "код выхода ${p.exitValue()}" } }
    return out
}

/**
 * Windows: настройки «Прокси-сервер» в реестре пользователя (HKCU, права администратора не нужны).
 * Прежние значения запоминаются в файле и возвращаются при выключении.
 */
class WindowsSystemProxy : SystemProxy {
    private val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    private val backup = File(AppPaths.dataDir, "previous-proxy.properties")

    private fun query(name: String): String? = runCatching {
        val line = exec("reg", "query", key, "/v", name).lines().firstOrNull { it.trim().startsWith(name) } ?: return null
        // «    ProxyServer    REG_SZ    127.0.0.1:10809»
        line.trim().split(Regex("\\s{2,}"), limit = 3).getOrNull(2)?.trim()
    }.getOrNull()

    private fun set(name: String, type: String, value: String) {
        exec("reg", "add", key, "/v", name, "/t", type, "/d", value, "/f")
    }

    override fun enable(httpPort: Int, socksPort: Int) {
        // Свои значения не затираем при повторном включении: бэкап делаем, только если прокси ещё не наш
        if (!isOurs(httpPort)) {
            val props = java.util.Properties()
            query("ProxyEnable")?.let { props["ProxyEnable"] = it }
            query("ProxyServer")?.let { props["ProxyServer"] = it }
            query("ProxyOverride")?.let { props["ProxyOverride"] = it }
            backup.outputStream().use { props.store(it, null) }
        }
        set("ProxyServer", "REG_SZ", "http=127.0.0.1:$httpPort;https=127.0.0.1:$httpPort;socks=127.0.0.1:$socksPort")
        set("ProxyOverride", "REG_SZ", "localhost;127.*;10.*;172.16.*;192.168.*;<local>")
        set("ProxyEnable", "REG_DWORD", "1")
        Log.d("реестр: ProxyEnable=${query("ProxyEnable")} ProxyServer=${query("ProxyServer")}")
        notifySettingsChanged()
    }

    override fun disable() {
        val saved = java.util.Properties().apply { if (backup.exists()) backup.inputStream().use { load(it) } }
        runCatching {
            saved.getProperty("ProxyServer")?.let { set("ProxyServer", "REG_SZ", it) }
            saved.getProperty("ProxyOverride")?.let { set("ProxyOverride", "REG_SZ", it) }
            // Прежнее ProxyEnable в реестре хранится как 0x0/0x1
            val enabled = saved.getProperty("ProxyEnable")?.removePrefix("0x")?.toIntOrNull(16) ?: 0
            set("ProxyEnable", "REG_DWORD", enabled.toString())
            notifySettingsChanged()
        }
        backup.delete()
    }

    override fun disableIfOurs(httpPort: Int, socksPort: Int) {
        if (isOurs(httpPort)) disable()
    }

    private fun isOurs(httpPort: Int): Boolean =
        query("ProxyEnable")?.endsWith("1") == true && query("ProxyServer")?.contains("127.0.0.1:$httpPort") == true

    /**
     * Без этого браузеры и системные службы замечают смену прокси не сразу.
     * Скрипт передаётся через -EncodedCommand (UTF-16LE в Base64): при обычной передаче в командной строке Windows
     * двойные кавычки внутри аргумента теряются, и `[DllImport("wininet.dll")]` не компилируется.
     */
    private fun notifySettingsChanged() {
        val script = """
            ${'$'}sig = '[DllImport("wininet.dll")] public static extern bool InternetSetOption(IntPtr h, int o, IntPtr b, int l);'
            ${'$'}t = Add-Type -MemberDefinition ${'$'}sig -Name W -Namespace N -PassThru
            ${'$'}null = ${'$'}t::InternetSetOption([IntPtr]::Zero, 39, [IntPtr]::Zero, 0)
            ${'$'}null = ${'$'}t::InternetSetOption([IntPtr]::Zero, 37, [IntPtr]::Zero, 0)
        """.trimIndent()
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        runCatching {
            exec("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded, timeoutSec = 20)
        }
            .onSuccess { Log.d("WinINet уведомлён об изменении прокси") }
            .onFailure { Log.d("не удалось уведомить WinINet: ${it.message?.take(300)}") }
    }
}

/** macOS: networksetup на всех сетевых службах (используется для проверки десктоп-версии на Mac). */
class MacSystemProxy : SystemProxy {
    private fun services(): List<String> =
        exec("/usr/sbin/networksetup", "-listallnetworkservices").lines().drop(1).filter { it.isNotBlank() && !it.startsWith("*") }

    private fun forEachService(action: (String) -> Unit) {
        services().parallelStream().forEach { s -> runCatching { action(s) } }
    }

    override fun enable(httpPort: Int, socksPort: Int) = forEachService { s ->
        exec("/usr/sbin/networksetup", "-setwebproxy", s, "127.0.0.1", httpPort.toString())
        exec("/usr/sbin/networksetup", "-setsecurewebproxy", s, "127.0.0.1", httpPort.toString())
        exec("/usr/sbin/networksetup", "-setsocksfirewallproxy", s, "127.0.0.1", socksPort.toString())
    }

    override fun disable() = forEachService { s ->
        for (flag in listOf("-setwebproxystate", "-setsecurewebproxystate", "-setsocksfirewallproxystate")) {
            runCatching { exec("/usr/sbin/networksetup", flag, s, "off") }
        }
    }

    override fun disableIfOurs(httpPort: Int, socksPort: Int) = forEachService { s ->
        fun ours(query: String, port: Int): Boolean {
            val text = runCatching { exec("/usr/sbin/networksetup", query, s) }.getOrDefault("")
            return "Enabled: Yes" in text && "Server: 127.0.0.1" in text && "Port: $port" in text
        }
        if (ours("-getwebproxy", httpPort)) exec("/usr/sbin/networksetup", "-setwebproxystate", s, "off")
        if (ours("-getsecurewebproxy", httpPort)) exec("/usr/sbin/networksetup", "-setsecurewebproxystate", s, "off")
        if (ours("-getsocksfirewallproxy", socksPort)) exec("/usr/sbin/networksetup", "-setsocksfirewallproxystate", s, "off")
    }
}
