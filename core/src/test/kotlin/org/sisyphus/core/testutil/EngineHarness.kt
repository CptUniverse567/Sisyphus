package org.sisyphus.core.testutil

import org.sisyphus.core.engine.SisyphusEngine
import org.sisyphus.core.persistence.AlarmRepository
import org.sisyphus.core.persistence.ChallengeRepository
import org.sisyphus.core.persistence.SettingsRepository
import org.sisyphus.core.sound.SoundResolver
import org.sisyphus.core.steps.StepGuard
import java.time.ZoneId

class EngineHarness(
    initialTimeMillis: Long = isoMillis("2026-01-01T05:00:00Z"),
    zone: ZoneId = ZoneId.of("UTC"),
    guard: StepGuard = StepGuard(),
) {
    val clock = FakeClock(initialTimeMillis, zone)
    val sensor = FakeStepSensor()
    val scheduler = FakeAlarmScheduler()
    val player = RecordingAlarmPlayer()
    val notifications = RecordingNotificationCenter()
    val store = InMemoryKeyValueStore()
    val soundAvailability = FakeSoundAvailability(playable = true)

    val challengeRepository = ChallengeRepository(store)
    val settingsRepository = SettingsRepository(store)
    val alarmRepository = AlarmRepository(store)
    private val guardValue = guard

    val engine =
        SisyphusEngine(
            clock = clock,
            sensor = sensor,
            scheduler = scheduler,
            alarmPlayer = player,
            notifications = notifications,
            challengeRepository = challengeRepository,
            settingsRepository = settingsRepository,
            soundResolver = SoundResolver(soundAvailability),
            alarmRepository = alarmRepository,
            stepGuard = guardValue,
        )

    fun armAndStart(
        requiredSteps: Int = 500,
        hour: Int = 6,
        minute: Int = 0,
    ) {
        engine.configureAlarm(requiredSteps, hour, minute)
        engine.onAlarmFired()
        engine.startChallenge()
    }

    fun rebuild(): SisyphusEngine =
        SisyphusEngine(
            clock = clock,
            sensor = sensor,
            scheduler = scheduler,
            alarmPlayer = player,
            notifications = notifications,
            challengeRepository = challengeRepository,
            settingsRepository = settingsRepository,
            soundResolver = SoundResolver(soundAvailability),
            alarmRepository = alarmRepository,
            stepGuard = guardValue,
        )
}
