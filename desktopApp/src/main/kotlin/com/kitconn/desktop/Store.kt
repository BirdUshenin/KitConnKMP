package com.kitconn.desktop

import com.kitconn.shared.data.KeyValueStore
import java.io.File
import java.util.Properties

/** Настройки в обычном .properties рядом с остальными данными приложения. */
class PropertiesStore(private val file: File = File(AppPaths.dataDir, "settings.properties")) : KeyValueStore {
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    @Synchronized
    override fun getString(key: String): String? = props.getProperty(key)

    @Synchronized
    override fun putString(key: String, value: String) {
        props.setProperty(key, value)
        file.outputStream().use { props.store(it, null) }
    }
}
