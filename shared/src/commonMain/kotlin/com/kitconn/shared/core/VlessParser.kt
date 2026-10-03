package com.kitconn.shared.core

data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String,
    val security: String,
    val type: String,
    val parameters: Map<String, String>,
    val name: String?,
) {
    val serverName: String? get() = parameters["sni"]
    val fingerprint: String? get() = parameters["fp"]
    val publicKey: String? get() = parameters["pbk"]
    val shortId: String? get() = parameters["sid"]
    val spiderX: String? get() = parameters["spx"]
    val flow: String? get() = parameters["flow"]
    val alpn: List<String> get() = parameters["alpn"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
}

object VlessParser {
    /** В ссылках `extra` бывает закодирован дважды (%257B...), поэтому снимаем кодирование несколько раз. */
    private const val MAX_EXTRA_DECODES = 3

    fun parse(url: String): VlessConfig {
        val trimmed = url.trim()
        require(trimmed.startsWith("vless://")) { "Invalid VLESS URL" }
        var rest = trimmed.removePrefix("vless://")

        val fragmentIndex = rest.indexOf('#')
        val name = if (fragmentIndex >= 0) percentDecode(rest.substring(fragmentIndex + 1)) else null
        if (fragmentIndex >= 0) rest = rest.substring(0, fragmentIndex)

        val queryIndex = rest.indexOf('?')
        val query = if (queryIndex >= 0) rest.substring(queryIndex + 1) else ""
        val authority = (if (queryIndex >= 0) rest.substring(0, queryIndex) else rest).substringBefore('/')

        val at = authority.lastIndexOf('@')
        require(at > 0) { "UUID is missing" }
        val uuid = percentDecode(authority.substring(0, at))
        val hostPort = authority.substring(at + 1)

        val host: String
        val portText: String?
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            require(close > 0) { "Address is missing" }
            host = hostPort.substring(1, close)
            portText = hostPort.substring(close + 1).removePrefix(":").ifEmpty { null }
        } else {
            host = hostPort.substringBeforeLast(':', hostPort)
            portText = if (':' in hostPort) hostPort.substringAfterLast(':') else null
        }
        require(host.isNotEmpty()) { "Address is missing" }
        val port = requireNotNull(portText?.toIntOrNull()) { "Port is missing" }

        // Пустые значения (sni=, host=, flow=) считаем отсутствующими, иначе в конфиг уйдут ""
        val parameters = LinkedHashMap<String, String>()
        query.split('&').filter { it.isNotEmpty() }.forEach { pair ->
            val key = percentDecode(pair.substringBefore('='), plusAsSpace = true)
            val value = percentDecode(pair.substringAfter('=', ""), plusAsSpace = true)
            if (value.isNotEmpty()) parameters[key] = value
        }

        parameters["extra"]?.let { extra ->
            var decoded = extra
            repeat(MAX_EXTRA_DECODES) {
                if (!decoded.trimStart().startsWith("{")) decoded = percentDecode(decoded)
            }
            parameters["extra"] = decoded
        }

        return VlessConfig(
            uuid = uuid,
            address = host,
            port = port,
            encryption = parameters["encryption"] ?: "none",
            security = parameters["security"] ?: "none",
            type = parameters["type"] ?: "tcp",
            parameters = parameters,
            name = name,
        )
    }
}
