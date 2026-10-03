package com.kitconn.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kitconn.shared.data.ConfigsApi
import com.kitconn.shared.presentation.AppDependencies
import com.kitconn.shared.ui.KitConnApp
import com.kitconn.shared.ui.drawCat
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.exitProcess

private const val APP_VERSION = 4
private const val VERSION_NAME = "4.0.0"

/** Иконка кота для трея и окна: рисуется кодом, без файлов. */
private object CatPainter : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() = drawCat(0f)
}

/** Второй экземпляр запускать нельзя: порты Xray заняты первым, а два приложения конфликтовали бы за прокси. */
private fun acquireSingleInstanceLock(): Boolean {
    val channel = RandomAccessFile(File(AppPaths.dataDir, "app.lock"), "rw").channel
    val lock = runCatching { channel.tryLock() }.getOrNull()
    // Канал и блокировку намеренно не закрываем: они живут до конца процесса
    return lock != null
}

fun main() {
    val proxy = SystemProxy.forCurrentOs()
    if (proxy == null) {
        System.err.println("Поддерживаются только Windows и macOS")
        exitProcess(1)
    }

    val selfTest = System.getenv("KITCONN_SELFTEST") == "1"
    if (!selfTest && !acquireSingleInstanceLock()) exitProcess(0)

    // Страховка после аварийного завершения: снимаем наш прокси и убиваем осиротевшее ядро
    val ports = com.kitconn.shared.core.XrayInbound.LocalProxy()
    XrayProcess.killStale()
    proxy.disableIfOurs(ports.httpPort, ports.socksPort)

    val controller = DesktopVpnController(XrayProcess(AppPaths.xrayBinary(), ports.apiPort), proxy, ports)
    // Завершение через «Завершить задачу», выход из системы, Ctrl+C: возвращаем прокси, иначе интернет пропадёт
    Runtime.getRuntime().addShutdownHook(Thread { controller.shutdown() })

    val deps = AppDependencies(
        vpn = controller,
        store = PropertiesStore(),
        api = ConfigsApi("https://kitconn-api.ilyaushenin.ru/", Secrets.API_TOKEN, APP_VERSION),
        versionName = VERSION_NAME,
    )

    if (selfTest) {
        runSelfTest(deps, controller)
        return
    }

    application {
        var windowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState(size = DpSize(390.dp, 780.dp), position = WindowPosition(Alignment.Center))

        Tray(
            icon = CatPainter,
            tooltip = "KitConn VPN",
            onAction = { windowVisible = true },
            menu = {
                Item("Открыть") { windowVisible = true }
                Separator()
                Item("Выйти") {
                    controller.shutdown()
                    exitApplication()
                }
            },
        )

        Window(
            onCloseRequest = { windowVisible = false },   // закрытие окна прячет его в трей, VPN продолжает работать
            visible = windowVisible,
            title = "KitConn VPN",
            icon = CatPainter,
            state = windowState,
            resizable = false,
        ) {
            KitConnApp(deps)
        }
    }
}
