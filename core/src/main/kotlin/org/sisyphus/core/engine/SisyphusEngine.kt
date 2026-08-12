package org.sisyphus.core.engine

import org.sisyphus.core.alarm.AlarmRestoreResult
import org.sisyphus.core.alarm.AlarmScheduleManager
import org.sisyphus.core.alarm.AlarmTimeCalculator
import org.sisyphus.core.challenge.Challenge
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.challenge.ChallengeStateMachine
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

class SisyphusEngine(
    private val clock: Clock,
    private val sensor: StepSensor,
    private val scheduler: AlarmScheduler,
    private val alarmPlayer: AlarmPlayer,
    private val notifications: NotificationCenter,
    private val challengeRepository: ChallengeRepository,
    private val settingsRepository: SettingsRepository,
    private val soundResolver: SoundResolver,
    private val stateMachine: ChallengeStateMachine = ChallengeStateMachine(),
    private val stepGuard: StepGuard = StepGuard(),
) {
    private var challenge: Challenge
    private var settings: AppSettings
    private val accountant = StepAccountant(stepGuard)
    private val alarmManager = AlarmScheduleManager(scheduler, AlarmTimeCalculator(clock))

    init {
        settings = settingsRepository.load() ?: AppSettings()
        challenge = challengeRepository.load() ?: Challenge(requiredSteps = settings.requiredSteps)
        if (challenge.state != ChallengeState.IDLE) {
            accountant.restoreBaseline(challenge.sensorBaseline)
        }
        if (challenge.isComplete && challenge.state != ChallengeState.COMPLETED) {
            challenge = challenge.copy(state = ChallengeState.COMPLETED)
        }
    }

    fun snapshot(): ChallengeViewState = ChallengeViewState.of(challenge)

    fun configureAlarm(
        requiredSteps: Int,
        hour: Int,
        minute: Int,
    ): ChallengeViewState {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
        val fireAt = alarmManager.peekFireTime(hour, minute)
        challenge = stateMachine.arm(challenge, requiredSteps, fireAt)
        alarmManager.scheduleAt(fireAt)
        settings = settings.copy(requiredSteps = requiredSteps, alarmHour = hour, alarmMinute = minute)
        persist()
        return snapshot()
    }

    fun onAlarmFired(): ChallengeViewState {
        challenge = stateMachine.alarmFired(challenge)
        alarmPlayer.start(soundResolver.resolve(settings.soundSelection))
        notifications.show("Sisyphus", "Walk to silence the alarm.")
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
    }

    fun restoreAlarmOnBoot(): ChallengeViewState {
        val fireAt = challenge.alarmFireTimeMillis
        if (challenge.state != ChallengeState.ARMED || fireAt == null) return snapshot()
        return when (alarmManager.restoreOrTrigger(fireAt, clock.currentTimeMillis())) {
            AlarmRestoreResult.RESCHEDULED -> snapshot()
            AlarmRestoreResult.DUE -> onAlarmFired()
        }
    }

    fun resumeChallengeSound() {
        if (challenge.state == ChallengeState.RINGING || challenge.state == ChallengeState.CHALLENGE_ACTIVE) {
            alarmPlayer.start(soundResolver.resolve(settings.soundSelection))
        }
    }

    fun currentSettings(): AppSettings = settings

    private fun persist() {
        challengeRepository.save(challenge)
        settingsRepository.save(settings)
    }
}
