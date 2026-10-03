package com.kitconn.desktop

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Журнал в %APPDATA%\KitConn\kitconn.log: по нему видно, на каком шаге подключение не заработало. */
object Log {
    private val file = File(AppPaths.dataDir, "kitconn.log")
    private val format = SimpleDateFormat("HH:mm:ss.SSS")

    @Synchronized
    fun d(message: String) {
        runCatching {
            // Журнал не должен расти бесконечно
            if (file.length() > 1_000_000) file.writeText("")
            file.appendText("${format.format(Date())} $message\n")
        }
    }
}
