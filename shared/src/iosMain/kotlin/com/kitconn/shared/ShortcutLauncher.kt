package com.kitconn.shared

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Запуск быстрых команд iOS. Команды с действием «Установить VPN» пользователь создаёт один раз:
 * они включают и выключают VPN-конфигурацию, созданную сторонним клиентом (см. iosApp/SHORTCUTS.md).
 */
internal object ShortcutLauncher {
    const val ON = "KitConn On"
    const val OFF = "KitConn Off"

    // По завершении команда возвращает пользователя в наше приложение
    private const val BACK = "kitconn://done"

    fun run(name: String): Boolean {
        val url = NSURL.URLWithString(
            "shortcuts://x-callback-url/run-shortcut?name=${percentEncode(name)}" +
                "&x-success=$BACK&x-cancel=$BACK&x-error=$BACK",
        ) ?: return false
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return true
    }
}
