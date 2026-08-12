package org.sisyphus.core.persistence

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.testutil.EngineHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceTest {
    @Test
    fun `armed challenge survives process death`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(requiredSteps = 500, hour = 6, minute = 0)
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)

        val rebuilt = harness.rebuild()
        val s = rebuilt.snapshot()
        assertEquals(ChallengeState.ARMED, s.state)
        assertEquals(500, s.remainingSteps)
        assertEquals(0, s.completedSteps)
    }

    @Test
    fun `183 of 500 completed survives recovery as 317 remaining`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)
        assertEquals(317, harness.engine.snapshot().remainingSteps)

        val rebuilt = harness.rebuild()
        val s = rebuilt.snapshot()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, s.state)
        assertEquals(183, s.completedSteps)
        assertEquals(317, s.remainingSteps)
        assertTrue(s.remainingSteps != 500, "recovery must not reset progress to the full requirement")
        assertTrue(s.remainingSteps != 0, "recovery must not zero remaining progress")
    }

    @Test
    fun `progress continues after recovery from a new sensor reading`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val rebuilt = harness.rebuild()
        harness.sensor.setReading(283)
        rebuilt.onSensorEvent(283)

        val s = rebuilt.snapshot()
        assertEquals(283, s.completedSteps)
        assertEquals(217, s.remainingSteps)
    }

    @Test
    fun `activity recreation does not reset progress`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 1000)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val recreatedActivity = harness.rebuild()
        assertEquals(250, recreatedActivity.snapshot().completedSteps)
        assertEquals(750, recreatedActivity.snapshot().remainingSteps)
    }

    @Test
    fun `backgrounding and reopening preserves the active challenge`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val reopened = harness.rebuild()
        assertEquals(183, reopened.snapshot().completedSteps)
        assertEquals(317, reopened.snapshot().remainingSteps)
    }

    @Test
    fun `service recreation restores the active challenge`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val recreatedService = harness.rebuild()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, recreatedService.snapshot().state)
        assertEquals(250, recreatedService.snapshot().completedSteps)
    }

    @Test
    fun `ringing state survives process death and sound can resume`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.RINGING, rebuilt.snapshot().state)
        rebuilt.resumeChallengeSound()
        assertTrue(harness.player.isPlaying)
        assertEquals(2, harness.player.starts.size)
    }

    @Test
    fun `completed challenge survives process death until acknowledged`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, rebuilt.snapshot().state)
        assertEquals(0, rebuilt.snapshot().remainingSteps)
    }

    @Test
    fun `acknowledging a completed challenge clears persisted challenge state`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        harness.engine.acknowledgeCompletion()

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
        assertTrue(ChallengeRepository.KEY_STATE !in harness.store.keys())
    }

    @Test
    fun `settings survive process death`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.CustomFile("content://com.example/sound.ogg"))
        harness.engine.configureAlarm(750, 7, 30)

        val rebuilt = harness.rebuild()
        val settings = rebuilt.currentSettings()
        assertEquals(750, settings.requiredSteps)
        assertEquals(7, settings.alarmHour)
        assertEquals(30, settings.alarmMinute)
        assertEquals(SoundSelection.CustomFile("content://com.example/sound.ogg"), settings.soundSelection)
    }

    @Test
    fun `corrupted persisted state falls back to a clean idle challenge`() {
        val harness = EngineHarness()
        harness.store.putString(ChallengeRepository.KEY_STATE, "NOT_A_STATE")
        harness.store.putInt(ChallengeRepository.KEY_REQUIRED_STEPS, 500)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
    }

    @Test
    fun `out of range persisted requirement is treated as missing`() {
        val harness = EngineHarness()
        harness.store.putString(ChallengeRepository.KEY_STATE, ChallengeState.ARMED.name)
        harness.store.putInt(ChallengeRepository.KEY_REQUIRED_STEPS, 999999)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
    }

    @Test
    fun `process death after exactly reaching the requirement restores completed`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, rebuilt.snapshot().state)
        assertEquals(0, rebuilt.snapshot().remainingSteps)
    }

    @Test
    fun `reboot simulation preserves persisted challenge and clears the platform alarm`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)

        harness.scheduler.reset()
        harness.clock.set(harness.clock.currentTimeMillis() + 1_000_000)

        val rebuilt = harness.rebuild()
        assertEquals(ChallengeState.ARMED, rebuilt.snapshot().state)
        assertNull(harness.scheduler.pendingFireAtMillis())
    }
}
