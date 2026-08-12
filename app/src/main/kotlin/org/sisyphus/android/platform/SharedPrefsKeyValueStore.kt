package org.sisyphus.android.platform

import android.content.SharedPreferences
import org.sisyphus.core.platform.KeyValueStore

class SharedPrefsKeyValueStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    override fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putLong(
        key: String,
        value: Long,
    ) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun putInt(
        key: String,
        value: Int,
    ) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "sisyphus"
    }
}
