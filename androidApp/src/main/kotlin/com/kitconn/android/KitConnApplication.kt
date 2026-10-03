package com.kitconn.android

import android.app.Application
import android.content.Context
import com.kitconn.android.vpn.AndroidVpnController
import com.kitconn.shared.data.ConfigsApi
import com.kitconn.shared.data.KeyValueStore
import com.kitconn.shared.presentation.AppDependencies

class KitConnApplication : Application() {
    lateinit var vpnController: AndroidVpnController
        private set
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        vpnController = AndroidVpnController(this)
        dependencies = AppDependencies(
            vpn = vpnController,
            store = PrefsStore(getSharedPreferences("kitconn_prefs", Context.MODE_PRIVATE)),
            api = ConfigsApi(
                baseUrl = "https://kitconn-api.ilyaushenin.ru/",
                token = Secrets.API_TOKEN,
                appVersion = BuildConfig.VERSION_CODE,
            ),
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}

private class PrefsStore(private val prefs: android.content.SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
