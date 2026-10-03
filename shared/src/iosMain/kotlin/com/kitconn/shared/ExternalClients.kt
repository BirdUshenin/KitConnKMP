package com.kitconn.shared

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

/**
 * Передача ссылки в стороннее VLESS-приложение. Нужна, пока в iOS-версии нет своего туннеля
 * (Network Extension требует платного аккаунта Apple Developer).
 * Схемы должны быть перечислены в `LSApplicationQueriesSchemes` в Info.plist, иначе canOpenURL вернёт false.
 */
internal object ExternalClients {
    private class Client(val name: String, val scheme: String, val path: String)

    // Формат ссылок импорта у клиентов; если у клиента формат изменится, достаточно поправить здесь
    private val clients = listOf(
        Client("Happ", "happ", "add"),
        Client("v2RayTun", "v2raytun", "import"),
        Client("Streisand", "streisand", "import"),
        Client("Hiddify", "hiddify", "import"),
    )

    class Result(val message: String, val opened: Boolean)

    /** Копирует ссылку и открывает установленный клиент; [Result.opened] — удалось ли открыть клиент. */
    fun handOff(link: String): Result {
        // Ссылку всегда кладём в буфер: если импорт не сработает, её можно вставить вручную
        UIPasteboard.generalPasteboard.string = link
        val encoded = percentEncode(link)
        val app = UIApplication.sharedApplication
        for (client in clients) {
            val url = NSURL.URLWithString("${client.scheme}://${client.path}/$encoded") ?: continue
            if (app.canOpenURL(url)) {
                app.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
                return Result("Ссылка передана в ${client.name} и скопирована в буфер", opened = true)
            }
        }
        return Result("Ссылка скопирована. Установите клиент с VLESS (Happ, v2RayTun, Streisand) и вставьте её", opened = false)
    }
}

private const val HEX = "0123456789ABCDEF"

/** Кодируем всё, кроме незарезервированных символов: получатель один раз декодирует и получит исходную строку. */
internal fun percentEncode(s: String): String {
    val out = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            out.append(ch)
        } else {
            out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }
    return out.toString()
}
