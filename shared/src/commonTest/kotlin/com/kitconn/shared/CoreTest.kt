package com.kitconn.shared

import com.kitconn.shared.core.CountryCode
import com.kitconn.shared.core.VlessParser
import com.kitconn.shared.core.XrayConfigBuilder
import com.kitconn.shared.core.XrayInbound
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    // Реальная ссылка с двойным кодированием extra и пустыми sni/host/flow
    private val xhttpUrl = "vless://66274ca4-9168-46d7-bcc8-0d9d88af290d@kitconn.ilyaushenin.ru:443?flow=&type=xhttp&host=&path=/kit&mode=packet-up&extra=%257B%250A%2520%2520%2522mode%2522%2520:%2520%2522packet-up%2522,%250A%2520%2520%2522xmux%2522%2520:%2520%257B%250A%2520%2520%2520%2520%2522maxConnections%2522%2520:%25202%250A%2520%2520%257D,%250A%2520%2520%2522xPaddingObfsMode%2522%2520:%2520true%250A%257D&security=tls&sni=&fp=firefox&allowInsecure=0&echfq=none#%F0%9F%87%A9%F0%9F%87%AAKitConnX-Germany(XT)"

    @Test
    fun parsesDoubleEncodedExtra() {
        val c = VlessParser.parse(xhttpUrl)
        assertEquals("kitconn.ilyaushenin.ru", c.address)
        assertEquals(443, c.port)
        assertEquals("xhttp", c.type)
        assertNull(c.serverName, "пустой sni считается отсутствующим")
        assertNull(c.flow)
        assertTrue(c.parameters.getValue("extra").startsWith("{"))
        assertEquals("🇩🇪KitConnX-Germany(XT)", c.name)
    }

    @Test
    fun buildsXhttpConfig() {
        val json = Json.parseToJsonElement(XrayConfigBuilder.build(VlessParser.parse(xhttpUrl))).jsonObject
        val out = json.getValue("outbounds").jsonArray[0].jsonObject
        val stream = out.getValue("streamSettings").jsonObject
        val xhttp = stream.getValue("xhttpSettings").jsonObject
        assertEquals("kitconn.ilyaushenin.ru", xhttp.getValue("host").jsonPrimitive.content)
        assertEquals("/kit", xhttp.getValue("path").jsonPrimitive.content)
        assertTrue(xhttp.getValue("extra").jsonObject.getValue("xPaddingObfsMode").jsonPrimitive.boolean)
        assertNull(stream.getValue("tlsSettings").jsonObject["serverName"])
        assertNull(stream.getValue("tlsSettings").jsonObject["allowInsecure"])
        assertEquals("tun", json.getValue("inbounds").jsonArray[0].jsonObject.getValue("protocol").jsonPrimitive.content)
    }

    @Test
    fun parsesRealityWithFlow() {
        val c = VlessParser.parse("vless://id@1.2.3.4:443?type=tcp&security=reality&sni=a.com&fp=chrome&pbk=KEY&sid=ab&flow=xtls-rprx-vision#n")
        val out = Json.parseToJsonElement(XrayConfigBuilder.build(c)).jsonObject.getValue("outbounds").jsonArray[0].jsonObject
        val reality = out.getValue("streamSettings").jsonObject.getValue("realitySettings").jsonObject
        assertEquals("KEY", reality.getValue("publicKey").jsonPrimitive.content)
        val user = out.getValue("settings").jsonObject.getValue("vnext").jsonArray[0].jsonObject.getValue("users").jsonArray[0].jsonObject
        assertEquals("xtls-rprx-vision", user.getValue("flow").jsonPrimitive.content)
    }

    @Test
    fun socksInboundForIos() {
        val json = Json.parseToJsonElement(XrayConfigBuilder.build(VlessParser.parse(xhttpUrl), XrayInbound.Socks(10808))).jsonObject
        val inbound = json.getValue("inbounds").jsonArray[0].jsonObject
        assertEquals("socks", inbound.getValue("protocol").jsonPrimitive.content)
        assertEquals("10808", inbound.getValue("port").jsonPrimitive.content)
    }

    @Test
    fun localProxyInboundsForDesktop() {
        val json = Json.parseToJsonElement(
            XrayConfigBuilder.build(VlessParser.parse(xhttpUrl), XrayInbound.LocalProxy()),
        ).jsonObject
        val protocols = json.getValue("inbounds").jsonArray.map { it.jsonObject.getValue("protocol").jsonPrimitive.content }
        assertEquals(listOf("socks", "http", "dokodemo-door"), protocols)
        assertEquals("proxy", json.getValue("outbounds").jsonArray[0].jsonObject.getValue("tag").jsonPrimitive.content)
        assertEquals("api", json.getValue("api").jsonObject.getValue("tag").jsonPrimitive.content)
    }

    @Test
    fun parsesIpv6() {
        val c = VlessParser.parse("vless://id@[2001:db8::1]:8443?type=tcp#x")
        assertEquals("2001:db8::1", c.address)
        assertEquals(8443, c.port)
    }

    @Test
    fun flags() {
        assertEquals("🇩🇪", CountryCode.flagEmoji("Germany"))
        assertEquals("🇮🇹", CountryCode.flagEmoji("Италия"))
        assertEquals("🌐", CountryCode.flagEmoji("unknown land"))
    }
}
