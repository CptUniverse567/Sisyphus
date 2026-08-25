package org.sisyphus.core.alarm

import org.sisyphus.core.challenge.StepRequirement
import org.sisyphus.core.settings.SoundSelection
import java.time.DayOfWeek

enum class RepeatMode {
    ONCE,
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    CUSTOM,
}

/**
 * A single, independent alarm configuration.
 *
 * Each alarm owns its own schedule (time + repeat mode), enabled state, step requirement, and
 * sound. Alarm instances are immutable value objects; edits produce a new copy.
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val repeatMode: RepeatMode = RepeatMode.DAILY,
    val customDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val requiredSteps: Int = StepRequirement.DEFAULT.requiredSteps,
    val soundSelection: SoundSelection = SoundSelection.Bundled,
    val onceDate: java.time.LocalDate? = null,
) {
    init {
        StepRequirement(requiredSteps)
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
        require(repeatMode != RepeatMode.CUSTOM || customDays.isNotEmpty()) {
            "CUSTOM repeat mode requires at least one selected day"
        }
        require(repeatMode == RepeatMode.ONCE || onceDate == null) {
            "onceDate is only valid for ONCE repeat mode"
        }
        require(repeatMode == RepeatMode.ONCE || repeatMode != RepeatMode.ONCE || onceDate != null) {
            "ONCE repeat mode requires a onceDate"
        }
    }

    val isEnabled: Boolean get() = enabled
}
