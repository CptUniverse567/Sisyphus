package org.sisyphus.core.engine

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.testutil.EngineHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifecycleTest {
    @Test
    fun `activity is not the source of truth for challenge progress`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val rebuiltActivity = harness.rebuild()
        assertEquals(183, rebuiltActivity.snapshot().completedSteps)
        assertEquals(317, rebuiltActivity.snapshot().remainingSteps)
        assertEquals(harness.engine.snapshot().completedSteps, rebuiltActivity.snapshot().completedSteps)
    }

    @Test
    fun `process death during the alarm restores ringing and resumes the sound`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.RINGING, restored.snapshot().state)
        restored.resumeChallengeSound()
        assertTrue(harness.player.isPlaying)
    }

    @Test
    fun `process death after two hundred fifty steps keeps the challenge active`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, restored.snapshot().state)
        assertEquals(250, restored.snapshot().completedSteps)
        assertEquals(250, restored.snapshot().remainingSteps)
    }

    @Test
    fun `process death at completion keeps the challenge completed`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, restored.snapshot().state)
        assertEquals(0, restored.snapshot().remainingSteps)
    }

    @Test
    fun `challenge continues across service restart`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val restartedService = harness.rebuild()
        harness.sensor.setReading(300)
        restartedService.onSensorEvent(300)
        assertEquals(300, restartedService.snapshot().completedSteps)
    }

    @Test
    fun `challenge continues across backgrounding and reopening`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        harness.clock.advance(10_000)
        val reopened = harness.rebuild()
        assertEquals(183, reopened.snapshot().completedSteps)
        assertEquals(317, reopened.snapshot().remainingSteps)
    }

    @Test
    fun `notification interaction reflects challenge state`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(1, harness.notifications.shown.size)

        harness.engine.startChallenge()
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(2, harness.notifications.shown.size)
        assertEquals("Challenge complete. The rock stays down.", harness.notifications.shown.last().second)

        harness.engine.acknowledgeCompletion()
        assertEquals(1, harness.notifications.dismissCount)
    }

    @Test
    fun `persisted challenge restoration is authoritative over a fresh activity`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 1000)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val freshActivity = harness.rebuild()
        assertEquals(183, freshActivity.snapshot().completedSteps)
        assertEquals(817, freshActivity.snapshot().remainingSteps)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, freshActivity.snapshot().state)
    }

    @Test
    fun `simultaneous process death and completion recovery stays completed`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)

        val restored = harness.rebuild()
        assertEquals(ChallengeState.COMPLETED, restored.snapshot().state)
        restored.acknowledgeCompletion()
        assertEquals(ChallengeState.IDLE, restored.snapshot().state)

        val again = harness.rebuild()
        assertEquals(ChallengeState.IDLE, again.snapshot().state)
    }

    @Test
    fun `sensor events while idle, armed or ringing are ignored`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.sensor.setReading(100)
        harness.engine.onSensorEvent(100)
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)

        harness.engine.onAlarmFired()
        harness.sensor.setReading(200)
        harness.engine.onSensorEvent(200)
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertEquals(0, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `engine snapshot drives the ui and always reports consistent totals`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val s = harness.engine.snapshot()
        assertEquals(s.completedSteps + s.remainingSteps, s.requiredSteps)
        assertTrue(s.remainingSteps >= 0)
        assertTrue(s.completedSteps <= s.requiredSteps)
    }
}
