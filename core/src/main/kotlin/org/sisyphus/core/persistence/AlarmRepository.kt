package org.sisyphus.core.persistence

import org.sisyphus.core.alarm.Alarm
import org.sisyphus.core.alarm.RepeatMode
import org.sisyphus.core.platform.KeyValueStore
import org.sisyphus.core.settings.SoundSelection
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Persists the list of independent alarms in a [KeyValueStore].
 *
 * A stable index of alarm IDs is stored so the ordering and membership of the list survive process
 * death and reboot. Each alarm's fields are stored under keys scoped to its ID.
 */
class AlarmRepository(private val store: KeyValueStore) {
    fun saveAll(alarms: List<Alarm>) {
        // Remove entries for alarms no longer present.
        val existing = store.getString(KEY_INDEX)?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
        val currentIds = alarms.map { it.id }.toSet()
        existing.filter { it !in currentIds }.forEach { removeAlarmKeys(it) }

        store.putString(KEY_INDEX, alarms.joinToString(SEPARATOR) { it.id })
        alarms.forEach { save(it) }
    }

    fun loadAll(): List<Alarm> {
        val ids = store.getString(KEY_INDEX)?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: return emptyList()
        return ids.mapNotNull { load(it) }
    }

    private fun save(alarm: Alarm) {
        val p = idPrefix(alarm.id)
        store.putInt("$p.hour", alarm.hour)
        store.putInt("$p.minute", alarm.minute)
        store.putString("$p.repeat", alarm.repeatMode.name)
        store.putString("$p.customDays", alarm.customDays.joinToString(SEPARATOR) { it.name })
        store.putBoolean("$p.enabled", alarm.enabled)
        store.putInt("$p.steps", alarm.requiredSteps)
        store.putString("$p.sound", encodeSound(alarm.soundSelection))
        if (alarm.onceDate != null) {
            store.putString(
                "$p.onceDate",
                alarm.onceDate.toString(),
            )
        } else {
            store.remove("$p.onceDate")
        }
    }

    private fun load(id: String): Alarm? {
        val p = idPrefix(id)
        val hour = store.getInt("$p.hour") ?: return null
        val minute = store.getInt("$p.minute") ?: return null
        val repeatName = store.getString("$p.repeat") ?: return null
        val repeat = runCatching { RepeatMode.valueOf(repeatName) }.getOrNull() ?: return null
        val steps = store.getInt("$p.steps") ?: return null
        val customDays =
            store.getString("$p.customDays")
                ?.split(SEPARATOR)
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        val enabled = store.getBoolean("$p.enabled") ?: true
        val sound = decodeSound(store.getString("$p.sound"))
        val onceDate = store.getString("$p.onceDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        return runCatching {
            Alarm(
                id = id,
                hour = hour,
                minute = minute,
                repeatMode = repeat,
                customDays = customDays,
                enabled = enabled,
                requiredSteps = steps,
                soundSelection = sound,
                onceDate = onceDate,
            )
        }.getOrNull()
    }

    private fun removeAlarmKeys(id: String) {
        val p = idPrefix(id)
        store.remove("$p.hour")
        store.remove("$p.minute")
        store.remove("$p.repeat")
        store.remove("$p.customDays")
        store.remove("$p.enabled")
        store.remove("$p.steps")
        store.remove("$p.sound")
        store.remove("$p.onceDate")
    }

    private fun idPrefix(id: String) = "$KEY_PREFIX.$id"

    private fun encodeSound(selection: SoundSelection): String =
        when (selection) {
            SoundSelection.Bundled -> "bundled"
            is SoundSelection.SystemRingtone -> "system:${selection.uri}"
            is SoundSelection.CustomFile -> "custom:${selection.uri}"
        }

    private fun decodeSound(encoded: String?): SoundSelection =
        when {
            encoded == null || encoded == "bundled" -> SoundSelection.Bundled
            encoded.startsWith("system:") -> SoundSelection.SystemRingtone(encoded.removePrefix("system:"))
            encoded.startsWith("custom:") -> SoundSelection.CustomFile(encoded.removePrefix("custom:"))
            else -> SoundSelection.Bundled
        }

    companion object {
        const val KEY_INDEX = "alarms.index"
        const val KEY_PREFIX = "alarm"
        const val SEPARATOR = ","
    }
}
