package org.sisyphus.core.alarm

import org.sisyphus.core.challenge.StepRequirement
import org.sisyphus.core.settings.SoundSelection
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Input specification for creating or editing an [Alarm]. The engine validates and produces an
 * [Alarm] value object from this.
 */
data class AlarmSpec(
    val hour: Int,
    val minute: Int,
    val repeatMode: RepeatMode = RepeatMode.DAILY,
    val customDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val requiredSteps: Int = StepRequirement.DEFAULT.requiredSteps,
    val soundSelection: SoundSelection = SoundSelection.Bundled,
    val onceDate: LocalDate? = null,
) {
    init {
        StepRequirement(requiredSteps)
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
    }

    fun toAlarm(id: String): Alarm =
        Alarm(
            id = id,
            hour = hour,
            minute = minute,
            repeatMode = repeatMode,
            customDays = customDays,
            enabled = enabled,
            requiredSteps = requiredSteps,
            soundSelection = soundSelection,
            onceDate = onceDate,
        )
}
