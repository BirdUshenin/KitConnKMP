package com.kitconn.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kitconn.shared.ui.KitConnApp
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {

    private var pendingVpnPermission: CompletableDeferred<Boolean>? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingVpnPermission?.complete(result.resultCode == RESULT_OK)
            pendingVpnPermission = null
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as KitConnApplication
        app.vpnController.permissionRequester = { intent: Intent ->
            CompletableDeferred<Boolean>().also {
                pendingVpnPermission = it
                vpnPermission.launch(intent)
            }.await()
        }

        // Без этого разрешения (Android 13+) уведомление о работе VPN не покажется
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { KitConnApp(app.dependencies) }
    }

    override fun onDestroy() {
        (application as KitConnApplication).vpnController.permissionRequester = null
        super.onDestroy()
    }
}
