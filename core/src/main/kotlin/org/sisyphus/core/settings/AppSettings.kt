package org.sisyphus.core.settings

import org.sisyphus.core.challenge.StepRequirement

data class AppSettings(
    val requiredSteps: Int = StepRequirement.DEFAULT.requiredSteps,
    val alarmHour: Int = 6,
    val alarmMinute: Int = 0,
    val soundSelection: SoundSelection = SoundSelection.Bundled,
    val notificationsEnabled: Boolean = true,
) {
    init {
        StepRequirement(requiredSteps)
        require(alarmHour in 0..23) { "alarmHour must be in 0..23 but was $alarmHour" }
        require(alarmMinute in 0..59) { "alarmMinute must be in 0..59 but was $alarmMinute" }
    }
}
