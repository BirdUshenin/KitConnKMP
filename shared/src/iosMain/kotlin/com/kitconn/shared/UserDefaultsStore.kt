package com.kitconn.shared

import com.kitconn.shared.data.KeyValueStore
import platform.Foundation.NSUserDefaults

internal class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
}
