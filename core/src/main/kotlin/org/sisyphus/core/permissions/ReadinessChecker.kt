package org.sisyphus.core.permissions

enum class ReadinessRequirement {
    NOTIFICATIONS,
    EXACT_ALARMS,
    FULL_SCREEN_INTENT,
    STEP_SENSOR,
    ALARM_SOUND,
}

data class ReadinessReport(
    val notificationsAllowed: Boolean,
    val sensorAvailable: Boolean,
    val hasAlarmSound: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenAlarmAllowed: Boolean,
) {
    val isReady: Boolean
        get() =
            notificationsAllowed &&
                sensorAvailable &&
                hasAlarmSound &&
                exactAlarmsAllowed &&
                fullScreenAlarmAllowed

    val missingRequirements: List<ReadinessRequirement>
        get() =
            buildList {
                if (!notificationsAllowed) add(ReadinessRequirement.NOTIFICATIONS)
                if (!exactAlarmsAllowed) add(ReadinessRequirement.EXACT_ALARMS)
                if (!fullScreenAlarmAllowed) add(ReadinessRequirement.FULL_SCREEN_INTENT)
                if (!sensorAvailable) add(ReadinessRequirement.STEP_SENSOR)
                if (!hasAlarmSound) add(ReadinessRequirement.ALARM_SOUND)
            }

    val missing: List<String>
        get() = missingRequirements.map { messageFor(it) }

    fun messageFor(requirement: ReadinessRequirement): String =
        when (requirement) {
            ReadinessRequirement.NOTIFICATIONS ->
                "Notifications are disabled. Sisyphus cannot raise the alarm."
            ReadinessRequirement.EXACT_ALARMS ->
                "Exact alarm access is denied. Sisyphus cannot schedule the alarm precisely."
            ReadinessRequirement.FULL_SCREEN_INTENT ->
                "Full-screen alarm access is denied. The alarm may not appear over the lock screen."
            ReadinessRequirement.STEP_SENSOR ->
                "No step sensor is available. Sisyphus cannot track steps on this device."
            ReadinessRequirement.ALARM_SOUND ->
                "The alarm sound is unavailable. Choose a different sound."
        }
}

class ReadinessChecker {
    fun check(
        notificationsAllowed: Boolean,
        sensorAvailable: Boolean,
        hasAlarmSound: Boolean,
        exactAlarmsAllowed: Boolean,
        fullScreenAlarmAllowed: Boolean,
    ): ReadinessReport =
        ReadinessReport(
            notificationsAllowed = notificationsAllowed,
            sensorAvailable = sensorAvailable,
            hasAlarmSound = hasAlarmSound,
            exactAlarmsAllowed = exactAlarmsAllowed,
            fullScreenAlarmAllowed = fullScreenAlarmAllowed,
        )
}
