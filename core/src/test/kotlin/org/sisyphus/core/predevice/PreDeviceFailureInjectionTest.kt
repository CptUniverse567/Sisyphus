package org.sisyphus.core.predevice

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.permissions.ReadinessChecker
import org.sisyphus.core.persistence.ChallengeRepository
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.testutil.EngineHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreDeviceFailureInjectionTest {
    @Test
    fun `process death during alarm recovers to ringing with sound`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()

        val restored = harness.rebuild()
        assertEquals(ChallengeState.RINGING, restored.snapshot().state)
        restored.resumeChallengeSound()
        assertTrue(harness.player.isPlaying)
    }

    @Test
    fun `process death after two hundred fifty steps recovers active`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, restored.snapshot().state)
        assertEquals(250, restored.snapshot().completedSteps)
    }

    @Test
    fun `process death after five hundred steps recovers completed`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, restored.snapshot().state)
        assertEquals(0, restored.snapshot().remainingSteps)
    }

    @Test
    fun `activity recreation during the alarm keeps the challenge`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val recreated = harness.rebuild()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, recreated.snapshot().state)
        assertEquals(183, recreated.snapshot().completedSteps)
        assertEquals(317, recreated.snapshot().remainingSteps)
    }

    @Test
    fun `service restart keeps the challenge and continues counting`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val restarted = harness.rebuild()
        harness.sensor.setReading(283)
        restarted.onSensorEvent(283)
        assertEquals(283, restarted.snapshot().completedSteps)
    }

    @Test
    fun `sensor unavailable before the challenge start is explained not silent`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        harness.sensor.setUnavailable()

        val error = assertFailsWith<IllegalStateException> { harness.engine.startChallenge() }
        assertTrue(error.message.orEmpty().contains("sensor"))

        val report =
            ReadinessChecker().check(
                notificationsAllowed = true,
                sensorAvailable = false,
                hasAlarmSound = true,
                exactAlarmsAllowed = true,
                fullScreenAlarmAllowed = true,
            )
        assertTrue(report.missing.single().contains("step sensor"))
    }

    @Test
    fun `sensor reset during the challenge never grants steps`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        harness.sensor.setReading(0)
        harness.engine.onSensorEvent(0)
        assertEquals(250, harness.engine.snapshot().completedSteps)

        harness.sensor.setReading(40)
        harness.engine.onSensorEvent(40)
        assertEquals(290, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `duplicate sensor events never double count`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(100)
        harness.engine.onSensorEvent(100)
        harness.sensor.setReading(100)
        harness.engine.onSensorEvent(100)
        harness.sensor.setReading(100)
        harness.engine.onSensorEvent(100)

        assertEquals(100, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `corrupted persisted state recovers to a valid idle challenge`() {
        val harness = EngineHarness()
        harness.store.putString(ChallengeRepository.KEY_STATE, "MADE_UP")
        harness.store.putInt(ChallengeRepository.KEY_REQUIRED_STEPS, -5)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
        assertTrue(rebuilt.snapshot().remainingSteps >= 0)
    }

    @Test
    fun `missing persisted state starts clean`() {
        val harness = EngineHarness()
        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
        assertNull(rebuilt.snapshot().alarmFireTimeMillis)
    }

    @Test
    fun `missing custom alarm sound falls back to bundled`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.CustomFile("content://gone/gone.ogg"))
        harness.soundAvailability.setPlayable(false)

        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(org.sisyphus.core.sound.ResolvedSound.Bundled, harness.player.starts.single())
    }

    @Test
    fun `permission denial is reported with the exact missing item`() {
        val report =
            ReadinessChecker().check(
                notificationsAllowed = false,
                sensorAvailable = false,
                hasAlarmSound = false,
                exactAlarmsAllowed = false,
                fullScreenAlarmAllowed = false,
            )
        assertEquals(5, report.missing.size)
        report.missing.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `permission revocation after setup is detected`() {
        val checker = ReadinessChecker()
        val before = checker.check(true, true, true, true, true)
        assertTrue(before.isReady)
        val revoked = checker.check(true, true, true, false, true)
        assertTrue(!revoked.isReady)
        assertTrue(revoked.missing.single().contains("Exact alarm"))
    }

    @Test
    fun `reboot recovery reschedules a future alarm`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.scheduler.reset()
        harness.clock.advance(1_000_000)

        val restored = harness.rebuild()
        val result = restored.restoreAlarmOnBoot()
        assertEquals(ChallengeState.ARMED, result.state)
        assertTrue(harness.scheduler.pendingFireAtMillis() != null)
    }

    @Test
    fun `alarm cancellation while inactive is rejected`() {
        val harness = EngineHarness()
        val idle = harness.engine.snapshot()
        assertEquals(ChallengeState.IDLE, idle.state)
        assertFailsWith<IllegalStateException> { harness.engine.cancelAlarm() }
    }

    @Test
    fun `attempted duplicate alarm creation keeps a single schedule`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        assertFailsWith<IllegalStateException> { harness.engine.configureAlarm(500, 6, 0) }
        assertEquals(1, harness.scheduler.scheduleCalls.size)
    }

    @Test
    fun `completion under simultaneous lifecycle events stays completed`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)

        val afterProcessDeath = harness.rebuild()
        harness.sensor.setReading(900)
        afterProcessDeath.onSensorEvent(900)

        val afterAnotherRecreation = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, afterAnotherRecreation.snapshot().state)
        assertEquals(0, afterAnotherRecreation.snapshot().remainingSteps)
    }
}
