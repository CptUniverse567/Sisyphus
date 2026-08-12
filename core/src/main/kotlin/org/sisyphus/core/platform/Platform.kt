package org.sisyphus.core.platform

import java.time.ZoneId

interface Clock {
    fun currentTimeMillis(): Long

    fun zone(): ZoneId
}

enum class SensorStatus {
    AVAILABLE,
    UNAVAILABLE,
}

interface StepSensor {
    fun status(): SensorStatus

    fun currentReading(): Long?
}

interface AlarmScheduler {
    fun schedule(
        fireAtMillis: Long,
        tag: String,
    )

    fun cancel(tag: String)

    fun pendingFireAtMillis(): Long?
}

interface AlarmPlayer {
    fun start(sound: org.sisyphus.core.sound.ResolvedSound)

    fun stop()
}

interface NotificationCenter {
    fun show(
        title: String,
        body: String,
    )

    fun dismiss()
}

interface KeyValueStore {
    fun getString(key: String): String?

    fun getLong(key: String): Long?

    fun getInt(key: String): Int?

    fun getBoolean(key: String): Boolean?

    fun putString(
        key: String,
        value: String,
    )

    fun putLong(
        key: String,
        value: Long,
    )

    fun putInt(
        key: String,
        value: Int,
    )

    fun putBoolean(
        key: String,
        value: Boolean,
    )

    fun remove(key: String)

    fun clear()
}
