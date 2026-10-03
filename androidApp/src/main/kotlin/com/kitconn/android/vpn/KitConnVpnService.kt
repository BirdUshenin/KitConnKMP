package com.kitconn.android.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kitconn.android.R
import com.kitconn.shared.core.VlessParser
import com.kitconn.shared.core.XrayConfigBuilder
import com.kitconn.shared.vpn.VpnState

class KitConnVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayManager: XrayManager? = null
    private var connectedAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stopping VPN service via ACTION_STOP")
            stopVpn()
            return START_NOT_STICKY
        }

        val serverLabel = intent?.getStringExtra(EXTRA_SERVER_LABEL)
        // Сервис запущен через startForegroundService: уведомление нужно показать до любой работы
        showNotification(connected = false, serverLabel = serverLabel)

        val vlessUrl = intent?.getStringExtra(EXTRA_VLESS_URL)
        if (vlessUrl == null) {
            Log.e(TAG, "VLESS URL is missing")
            stopVpn()
            return START_NOT_STICKY
        }

        VpnBridge.state.value = VpnState.CONNECTING

        val builder = Builder()
            .setSession("KitConn")
            .addAddress("10.0.0.2", 24)
            .addAddress("fd00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)

        // Само приложение исключаем из туннеля, чтобы трафик ядра не зациклился
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Log.e(TAG, "Failed to disallow application", it) }

        vpnInterface = builder.establish()
        val tun = vpnInterface
        if (tun == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            VpnBridge.errors.tryEmit("Не удалось создать VPN-интерфейс")
            stopVpn()
            return START_NOT_STICKY
        }

        try {
            val xrayConfig = XrayConfigBuilder.build(VlessParser.parse(vlessUrl))
            xrayManager = XrayManager().also { it.start(config = xrayConfig, tunFd = tun.fd) }
            Log.d(TAG, "Xray started successfully")
            VpnBridge.state.value = VpnState.CONNECTED
            showNotification(connected = true, serverLabel = serverLabel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray", e)
            VpnBridge.errors.tryEmit("Не удалось запустить ядро: ${e.message}")
            stopVpn()
        }
        return START_STICKY
    }

    private fun showNotification(connected: Boolean, serverLabel: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Статус VPN", NotificationManager.IMPORTANCE_LOW))
        }

        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, KitConnVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        if (connected && connectedAt == 0L) connectedAt = System.currentTimeMillis()
        if (!connected) connectedAt = 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentTitle(if (connected) "Подключено" else "Подключение…")
            .setContentText(serverLabel)
            .setContentIntent(openApp)
            .addAction(0, "Отключить", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Тёмная подложка в цвет приложения; текст система подбирает светлый сама
            .setColor(0xFF0C0D14.toInt())
            .setColorized(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (connected) {
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(connectedAt)
        } else {
            builder.setProgress(0, 0, true)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), type)
    }

    override fun onRevoke() {
        Log.d(TAG, "VPN service revoked by system settings")
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        runCatching { xrayManager?.stop() }.onFailure { Log.e(TAG, "Error stopping xray manager", it) }
        xrayManager = null
        runCatching { vpnInterface?.close() }.onFailure { Log.e(TAG, "Error closing VPN interface", it) }
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        VpnBridge.state.value = VpnState.DISCONNECTED
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KitConnVpnService"
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_VLESS_URL = "extra_vless_url"
        const val EXTRA_SERVER_LABEL = "extra_server_label"
        const val ACTION_STOP = "com.kitconn.android.ACTION_STOP"
    }
}
