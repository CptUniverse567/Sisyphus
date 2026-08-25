package org.sisyphus.core.engine

import org.sisyphus.core.alarm.Alarm
import org.sisyphus.core.alarm.AlarmRecurrenceCalculator
import org.sisyphus.core.alarm.AlarmRestoreResult
import org.sisyphus.core.alarm.AlarmScheduleManager
import org.sisyphus.core.alarm.AlarmSpec
import org.sisyphus.core.alarm.RepeatMode
import org.sisyphus.core.challenge.Challenge
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.challenge.ChallengeStateMachine
import org.sisyphus.core.persistence.AlarmRepository
import org.sisyphus.core.persistence.ChallengeRepository
import org.sisyphus.core.persistence.SettingsRepository
import org.sisyphus.core.platform.AlarmPlayer
import org.sisyphus.core.platform.AlarmScheduler
import org.sisyphus.core.platform.Clock
import org.sisyphus.core.platform.NotificationCenter
import org.sisyphus.core.platform.SensorStatus
import org.sisyphus.core.platform.StepSensor
import org.sisyphus.core.settings.AppSettings
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.sound.SoundResolver
import org.sisyphus.core.steps.StepAccountant
import org.sisyphus.core.steps.StepGuard

/**
 * Owns all Sisyphus state: the list of independent [Alarm]s and the single active challenge
 * (the occurrence currently ringing / being walked). The Activity is never the source of truth;
 * every mutation is persisted immediately.
 */
class SisyphusEngine(
    private val clock: Clock,
    private val sensor: StepSensor,
    private val scheduler: AlarmScheduler,
    private val alarmPlayer: AlarmPlayer,
    private val notifications: NotificationCenter,
    private val challengeRepository: ChallengeRepository,
    private val settingsRepository: SettingsRepository,
    private val soundResolver: SoundResolver,
    private val alarmRepository: AlarmRepository,
    private val stateMachine: ChallengeStateMachine = ChallengeStateMachine(),
    private val stepGuard: StepGuard = StepGuard(),
) {
    private var alarms: MutableList<Alarm>
    private var settings: AppSettings
    private var challenge: Challenge
    private val accountant = StepAccountant(stepGuard)
    private val alarmManager = AlarmScheduleManager(scheduler, org.sisyphus.core.alarm.AlarmTimeCalculator(clock))
    private val recurrence = AlarmRecurrenceCalculator(clock)
    private var nextAlarmId: Int

    init {
        settings = settingsRepository.load() ?: AppSettings()
        alarms = alarmRepository.loadAll().toMutableList()
        migrateLegacyAlarmIfNeeded()
        nextAlarmId = (alarms.mapNotNull { it.id.removePrefix(ID_PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1
        challenge = challengeRepository.load() ?: Challenge(requiredSteps = settings.requiredSteps)
        if (challenge.state != ChallengeState.IDLE) {
            accountant.restoreBaseline(challenge.sensorBaseline)
        }
        if (challenge.isComplete && challenge.state != ChallengeState.COMPLETED) {
            challenge = challenge.copy(state = ChallengeState.COMPLETED)
        }
    }

    private fun migrateLegacyAlarmIfNeeded() {
        if (alarms.isNotEmpty()) return
        val s = settingsRepository.load() ?: return
        if (s.requiredSteps < 1) return
        val legacy =
            Alarm(
                id = LEGACY_ALARM_ID,
                hour = s.alarmHour,
                minute = s.alarmMinute,
                repeatMode = RepeatMode.DAILY,
                enabled = true,
                requiredSteps = s.requiredSteps,
                soundSelection = s.soundSelection,
            )
        alarms.add(legacy)
        alarmRepository.saveAll(alarms)
    }

    fun snapshot(): ChallengeViewState = ChallengeViewState.of(challenge)

    fun alarms(): List<Alarm> = alarms.toList()

    // ------------------------------------------------------------------
    // Multi-alarm CRUD
    // ------------------------------------------------------------------

    fun addAlarm(spec: AlarmSpec): List<Alarm> {
        val alarm = spec.toAlarm(nextId())
        alarms.add(alarm)
        if (alarm.enabled) scheduleAlarm(alarm)
        persistAlarms()
        return alarms()
    }

    fun updateAlarm(
        id: String,
        spec: AlarmSpec,
    ): List<Alarm> {
        val idx = alarms.indexOfFirst { it.id == id }
        if (idx < 0) return alarms()
        val updated = spec.toAlarm(id)
        alarms[idx] = updated
        // Recompute the schedule for the edited alarm.
        scheduler.cancel(id)
        if (updated.enabled) scheduleAlarm(updated)
        persistAlarms()
        return alarms()
    }

    fun setAlarmEnabled(
        id: String,
        enabled: Boolean,
    ): List<Alarm> {
        val idx = alarms.indexOfFirst { it.id == id }
        if (idx < 0) return alarms()
        val updated = alarms[idx].copy(enabled = enabled)
        alarms[idx] = updated
        if (enabled) {
            scheduleAlarm(updated)
        } else {
            scheduler.cancel(id)
        }
        persistAlarms()
        return alarms()
    }

    fun deleteAlarm(id: String): List<Alarm> {
        val existed = alarms.any { it.id == id }
        alarms.removeAll { it.id == id }
        scheduler.cancel(id)
        if (challenge.alarmId == id && challenge.state != ChallengeState.IDLE) {
            // Deleting the alarm whose occurrence is active clears that occurrence.
            challenge =
                challenge.copy(
                    state = ChallengeState.IDLE,
                    completedSteps = 0,
                    sensorBaseline = null,
                    alarmFireTimeMillis = null,
                )
            accountant.reset()
            alarmPlayer.stop()
            notifications.dismiss()
        }
        if (existed) persistAlarms()
        persistChallenge()
        return alarms()
    }

    // ------------------------------------------------------------------
    // Legacy single-alarm API (kept for backward compatibility)
    // ------------------------------------------------------------------

    fun configureAlarm(
        requiredSteps: Int,
        hour: Int,
        minute: Int,
    ): ChallengeViewState {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
        // Preserve the v1.0 rule: cannot configure a new alarm while a challenge is active.
        if (challenge.state != ChallengeState.IDLE) {
            throw IllegalStateException("Cannot configure an alarm in state ${challenge.state}")
        }
        val spec =
            AlarmSpec(
                hour = hour,
                minute = minute,
                repeatMode = RepeatMode.DAILY,
                requiredSteps = requiredSteps,
                soundSelection = settings.soundSelection,
            )
        val alarm = spec.toAlarm(LEGACY_ALARM_ID)
        val idx = alarms.indexOfFirst { it.id == LEGACY_ALARM_ID }
        if (idx >= 0) alarms[idx] = alarm else alarms.add(alarm)

        val fireAt = alarmManager.peekFireTime(hour, minute)
        challenge = stateMachine.arm(challenge, requiredSteps, fireAt).copy(alarmId = LEGACY_ALARM_ID)
        alarmManager.scheduleAt(fireAt)
        settings = settings.copy(requiredSteps = requiredSteps, alarmHour = hour, alarmMinute = minute)
        persistAlarms()
        persist()
        return snapshot()
    }

    fun onAlarmFired(): ChallengeViewState {
        // Legacy: fire the currently armed occurrence.
        if (challenge.state != ChallengeState.ARMED) {
            throw IllegalStateException("Alarm cannot fire from state ${challenge.state}")
        }
        challenge = stateMachine.alarmFired(challenge)
        alarmPlayer.start(soundResolver.resolve(settings.soundSelection))
        notifications.show("Sisyphus", "Walk to silence the alarm.")
        rescheduleOccurrence(challenge.alarmId ?: LEGACY_ALARM_ID)
        persist()
        return snapshot()
    }

    fun onAlarmFired(alarmId: String): ChallengeViewState {
        val alarm = alarms.firstOrNull { it.id == alarmId } ?: return snapshot()
        if (!alarm.enabled) return snapshot()
        // Only one occurrence can be active at a time; a second firing is skipped (missed).
        if (challenge.state != ChallengeState.IDLE) return snapshot()
        val fireAt = clock.currentTimeMillis()
        challenge = stateMachine.arm(challenge, alarm.requiredSteps, fireAt).copy(alarmId = alarm.id)
        challenge = stateMachine.alarmFired(challenge)
        alarmPlayer.start(soundResolver.resolve(alarm.soundSelection))
        notifications.show("Sisyphus", "Walk to silence the alarm.")
        rescheduleOccurrence(alarm.id)
        persist()
        return snapshot()
    }

    fun startChallenge(): ChallengeViewState {
        val status = sensor.status()
        val reading = sensor.currentReading()
        if (status != SensorStatus.AVAILABLE || reading == null) {
            throw IllegalStateException("Step sensor unavailable: cannot start the challenge")
        }
        challenge = stateMachine.startChallenge(challenge, reading)
        accountant.startBaseline(reading)
        persist()
        return snapshot()
    }

    fun onSensorEvent(currentReading: Long): ChallengeViewState {
        if (challenge.state != ChallengeState.CHALLENGE_ACTIVE) return snapshot()
        val additional = accountant.onSensorReading(currentReading)
        val next = stateMachine.progress(challenge, additional)
        val updated = next.copy(sensorBaseline = accountant.baseline ?: next.sensorBaseline)
        if (updated.state == ChallengeState.COMPLETED) {
            alarmPlayer.stop()
            notifications.show("Sisyphus", "Challenge complete. The rock stays down.")
        }
        challenge = updated
        persist()
        return snapshot()
    }

    fun acknowledgeCompletion(): ChallengeViewState {
        challenge = stateMachine.acknowledge(challenge)
        notifications.dismiss()
        persist()
        return snapshot()
    }

    fun cancelAlarm(): ChallengeViewState {
        challenge = stateMachine.cancel(challenge)
        alarmManager.cancel()
        alarmPlayer.stop()
        notifications.dismiss()
        persist()
        return snapshot()
    }

    fun selectSound(selection: SoundSelection) {
        settings = settings.copy(soundSelection = selection)
        settingsRepository.save(settings)
        val idx = alarms.indexOfFirst { it.id == LEGACY_ALARM_ID }
        if (idx >= 0) {
            alarms[idx] = alarms[idx].copy(soundSelection = selection)
            persistAlarms()
        }
    }

    fun restoreAlarmOnBoot(): ChallengeViewState {
        // 1) Recover an in-flight / armed occurrence for the active challenge.
        val fireAt = challenge.alarmFireTimeMillis
        if (challenge.state == ChallengeState.ARMED && fireAt != null) {
            when (alarmManager.restoreOrTrigger(fireAt, clock.currentTimeMillis())) {
                AlarmRestoreResult.RESCHEDULED -> Unit
                AlarmRestoreResult.DUE -> onAlarmFired()
            }
        }
        // 2) Reschedule every enabled alarm to its next valid occurrence.
        rescheduleAllEnabledAlarms()
        persist()
        return snapshot()
    }

    fun resumeChallengeSound() {
        if (challenge.state == ChallengeState.RINGING || challenge.state == ChallengeState.CHALLENGE_ACTIVE) {
            val sound =
                challenge.alarmId?.let { id -> alarms.firstOrNull { it.id == id }?.soundSelection }
                    ?: settings.soundSelection
            alarmPlayer.start(soundResolver.resolve(sound))
        }
    }

    fun currentSettings(): AppSettings = settings

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun nextId(): String = "$ID_PREFIX${nextAlarmId++}"

    private fun scheduleAlarm(alarm: Alarm) {
        val fireAt = recurrence.nextOccurrenceMillis(alarm) ?: return
        scheduler.schedule(fireAt, alarm.id)
    }

    /** After an occurrence fires, schedule the next occurrence (recurring) or cancel (one-shot past). */
    private fun rescheduleOccurrence(id: String) {
        val alarm = alarms.firstOrNull { it.id == id } ?: return
        if (!alarm.enabled) return
        val next = recurrence.nextOccurrenceMillis(alarm)
        if (next != null) {
            scheduler.schedule(next, alarm.id)
        } else {
            scheduler.cancel(id)
        }
    }

    private fun rescheduleAllEnabledAlarms() {
        alarms.filter { it.enabled }.forEach { scheduleAlarm(it) }
        alarms.filter { !it.enabled }.forEach { scheduler.cancel(it.id) }
    }

    private fun persistAlarms() {
        alarmRepository.saveAll(alarms)
    }

    private fun persist() {
        challengeRepository.save(challenge)
        settingsRepository.save(settings)
        persistAlarms()
    }

    private fun persistChallenge() {
        challengeRepository.save(challenge)
    }

    companion object {
        const val LEGACY_ALARM_ID = "sisyphus_alarm"
        const val ID_PREFIX = "alarm-"
    }
}
