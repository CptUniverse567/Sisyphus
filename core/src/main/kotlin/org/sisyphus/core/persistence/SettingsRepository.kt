package org.sisyphus.core.persistence

import org.sisyphus.core.platform.KeyValueStore
import org.sisyphus.core.settings.AppSettings
import org.sisyphus.core.settings.SoundSelection

class SettingsRepository(private val store: KeyValueStore) {
    fun save(settings: AppSettings) {
        store.putInt(KEY_REQUIRED_STEPS, settings.requiredSteps)
        store.putInt(KEY_ALARM_HOUR, settings.alarmHour)
        store.putInt(KEY_ALARM_MINUTE, settings.alarmMinute)
        store.putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
        store.putString(
            KEY_SOUND_TYPE,
            when (settings.soundSelection) {
                SoundSelection.Bundled -> TYPE_BUNDLED
                is SoundSelection.SystemRingtone -> TYPE_SYSTEM
                is SoundSelection.CustomFile -> TYPE_CUSTOM
            },
        )
        val uri =
            when (settings.soundSelection) {
                SoundSelection.Bundled -> null
                is SoundSelection.SystemRingtone -> settings.soundSelection.uri
                is SoundSelection.CustomFile -> settings.soundSelection.uri
            }
        if (uri != null) store.putString(KEY_SOUND_URI, uri) else store.remove(KEY_SOUND_URI)
    }

    fun load(): AppSettings? {
        val required = store.getInt(KEY_REQUIRED_STEPS) ?: return null
        if (required !in 1..10000) return null
        val hour = store.getInt(KEY_ALARM_HOUR) ?: 6
        val minute = store.getInt(KEY_ALARM_MINUTE) ?: 0
        val notifications = store.getBoolean(KEY_NOTIFICATIONS_ENABLED) ?: true
        val soundType = store.getString(KEY_SOUND_TYPE) ?: TYPE_BUNDLED
        val uri = store.getString(KEY_SOUND_URI)
        val sound =
            when (soundType) {
                TYPE_SYSTEM -> if (uri != null) SoundSelection.SystemRingtone(uri) else SoundSelection.Bundled
                TYPE_CUSTOM -> if (uri != null) SoundSelection.CustomFile(uri) else SoundSelection.Bundled
                else -> SoundSelection.Bundled
            }
        return AppSettings(
            requiredSteps = required,
            alarmHour = hour,
            alarmMinute = minute,
            soundSelection = sound,
            notificationsEnabled = notifications,
        )
    }

    companion object {
        const val KEY_REQUIRED_STEPS = "settings.requiredSteps"
        const val KEY_ALARM_HOUR = "settings.alarmHour"
        const val KEY_ALARM_MINUTE = "settings.alarmMinute"
        const val KEY_NOTIFICATIONS_ENABLED = "settings.notificationsEnabled"
        const val KEY_SOUND_TYPE = "settings.sound.type"
        const val KEY_SOUND_URI = "settings.sound.uri"

        const val TYPE_BUNDLED = "bundled"
        const val TYPE_SYSTEM = "system"
        const val TYPE_CUSTOM = "custom"
    }
}
