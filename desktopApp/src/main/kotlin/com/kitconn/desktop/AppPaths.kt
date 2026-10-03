package com.kitconn.desktop

import java.io.File

enum class DesktopOs { WINDOWS, MACOS, OTHER;
    companion object {
        val current: DesktopOs = System.getProperty("os.name").lowercase().let {
            when {
                "win" in it -> WINDOWS
                "mac" in it -> MACOS
                else -> OTHER
            }
        }
    }
}

object AppPaths {
    /** Папка настроек: %APPDATA%\KitConn, ~/Library/Application Support/KitConn, ~/.kitconn. */
    val dataDir: File by lazy {
        val home = System.getProperty("user.home")
        val dir = when (DesktopOs.current) {
            DesktopOs.WINDOWS -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "KitConn")
            DesktopOs.MACOS -> File(home, "Library/Application Support/KitConn")
            DesktopOs.OTHER -> File(home, ".kitconn")
        }
        dir.also { it.mkdirs() }
    }

    /** xray рядом с приложением: Compose кладёт ресурсы выбранной ОС в compose.application.resources.dir. */
    fun xrayBinary(): File {
        val name = if (DesktopOs.current == DesktopOs.WINDOWS) "xray.exe" else "xray"
        val resources = System.getProperty("compose.application.resources.dir")
        return File(resources ?: "appResources", name)
    }
}
