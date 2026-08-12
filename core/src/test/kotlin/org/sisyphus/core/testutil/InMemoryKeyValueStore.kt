package org.sisyphus.core.testutil

import org.sisyphus.core.platform.KeyValueStore

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun getLong(key: String): Long? = map[key]?.toLongOrNull()

    override fun getInt(key: String): Int? = map[key]?.toIntOrNull()

    override fun getBoolean(key: String): Boolean? = map[key]?.toBooleanStrictOrNull()

    override fun putString(
        key: String,
        value: String,
    ) {
        map[key] = value
    }

    override fun putLong(
        key: String,
        value: Long,
    ) {
        map[key] = value.toString()
    }

    override fun putInt(
        key: String,
        value: Int,
    ) {
        map[key] = value.toString()
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        map[key] = value.toString()
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }

    fun keys(): Set<String> = map.keys
}
