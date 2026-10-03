package com.kitconn.shared.data

/** Минимальное хранилище настроек; на платформах — SharedPreferences и NSUserDefaults. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class InMemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}
