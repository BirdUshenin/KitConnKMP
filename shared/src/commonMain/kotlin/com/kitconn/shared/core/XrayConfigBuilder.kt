package com.kitconn.shared.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Как ядро получает трафик: TUN-дескриптор (Android) или локальный SOCKS (iOS: туннель → tun2socks → SOCKS). */
sealed interface XrayInbound {
    data object Tun : XrayInbound
    data class Socks(val port: Int = 10808) : XrayInbound

    /**
     * Десктоп: локальные SOCKS и HTTP прокси (на них указывает системный прокси) и API статистики,
     * по которому приложение считает скорость.
     */
    data class LocalProxy(val socksPort: Int = 10808, val httpPort: Int = 10809, val apiPort: Int = 10085) : XrayInbound
}

object XrayConfigBuilder {
    /**
     * Конфиг Xray с TUN-входом: ядро получает файловый дескриптор туннеля от платформы
     * (VpnService на Android, NEPacketTunnelProvider на iOS).
     * `allowInsecure` из ссылки намеренно игнорируется: сервер конфигов не должен отключать проверку TLS.
     */
    fun build(config: VlessConfig, inbound: XrayInbound = XrayInbound.Tun): String {
        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", "vless")
            putJsonObject("settings") {
                putJsonArray("vnext") {
                    add(buildJsonObject {
                        put("address", config.address)
                        put("port", config.port)
                        putJsonArray("users") {
                            add(buildJsonObject {
                                put("id", config.uuid)
                                put("encryption", config.encryption)
                                config.flow?.let { put("flow", it) }
                            })
                        }
                    })
                }
            }
            putJsonObject("streamSettings") {
                put("network", config.type)
                put("security", config.security)

                if (config.security == "reality") {
                    putJsonObject("realitySettings") {
                        config.serverName?.let { put("serverName", it) }
                        put("fingerprint", config.fingerprint ?: "chrome")
                        config.publicKey?.let { put("publicKey", it) }
                        config.shortId?.let { put("shortId", it) }
                        config.spiderX?.let { put("spiderX", it) }
                    }
                }
                if (config.security == "tls") {
                    putJsonObject("tlsSettings") {
                        config.serverName?.let { put("serverName", it) }
                        config.fingerprint?.let { put("fingerprint", it) }
                        if (config.alpn.isNotEmpty()) {
                            put("alpn", JsonArray(config.alpn.map(::JsonPrimitive)))
                        }
                    }
                }
                if (config.type == "xhttp") {
                    put("xhttpSettings", xhttpSettings(config))
                }
            }
        }

        val root = buildJsonObject {
            putJsonArray("inbounds") {
                if (inbound is XrayInbound.LocalProxy) {
                    add(buildJsonObject {
                        put("tag", "socks-in")
                        put("listen", "127.0.0.1")
                        put("port", inbound.socksPort)
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            put("udp", true)
                            put("auth", "noauth")
                        }
                    })
                    add(buildJsonObject {
                        put("tag", "http-in")
                        put("listen", "127.0.0.1")
                        put("port", inbound.httpPort)
                        put("protocol", "http")
                    })
                    add(buildJsonObject {
                        put("tag", "api-in")
                        put("listen", "127.0.0.1")
                        put("port", inbound.apiPort)
                        put("protocol", "dokodemo-door")
                        putJsonObject("settings") { put("address", "127.0.0.1") }
                    })
                } else add(
                    when (inbound) {
                        XrayInbound.Tun -> buildJsonObject {
                            put("tag", "tun-in")
                            put("protocol", "tun")
                            putJsonObject("settings") {
                                put("name", "tun0")
                                put("mtu", 1500)
                            }
                        }
                        is XrayInbound.LocalProxy -> error("обработано выше")
                        is XrayInbound.Socks -> buildJsonObject {
                            put("tag", "socks-in")
                            put("listen", "127.0.0.1")
                            put("port", inbound.port)
                            put("protocol", "socks")
                            putJsonObject("settings") {
                                put("udp", true)
                                put("auth", "noauth")
                            }
                        }
                    },
                )
            }
            if (inbound is XrayInbound.LocalProxy) {
                putJsonObject("stats") {}
                putJsonObject("api") {
                    put("tag", "api")
                    putJsonArray("services") { add(JsonPrimitive("StatsService")) }
                }
                putJsonObject("policy") {
                    putJsonObject("system") {
                        put("statsOutboundUplink", true)
                        put("statsOutboundDownlink", true)
                    }
                }
                putJsonObject("routing") {
                    putJsonArray("rules") {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("inboundTag") { add(JsonPrimitive("api-in")) }
                            put("outboundTag", "api")
                        })
                    }
                }
            }
            putJsonArray("outbounds") {
                add(outbound)
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                })
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(JsonPrimitive("1.1.1.1"))
                    add(JsonPrimitive("8.8.8.8"))
                }
            }
        }
        return root.toString()
    }

    private fun xhttpSettings(config: VlessConfig): JsonObject {
        val p = config.parameters
        return buildJsonObject {
            put("host", p["host"] ?: config.serverName ?: config.address)
            p["path"]?.let { put("path", it) }
            p["mode"]?.let { put("mode", it) }
            p["extra"]?.let {
                val parsed = runCatching { Json.parseToJsonElement(it) }.getOrNull()
                require(parsed is JsonObject) { "Параметр extra не удалось разобрать как JSON" }
                put("extra", parsed)
            }
        }
    }
}
