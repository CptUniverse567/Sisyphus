package org.sisyphus.core.engine

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.testutil.EngineHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class SensorResumeRegressionTest {
    @Test
    fun `cumulative counter baseline never grants steps at start`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(48_000)
        harness.engine.onSensorEvent(48_000)
        assertEquals(500, harness.engine.snapshot().remainingSteps)
        assertEquals(0, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `reopening the app resumes from persisted progress and counts new steps`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        assertEquals(250, harness.engine.snapshot().remainingSteps)

        val reopened = harness.rebuild()
        harness.sensor.setReading(300)
        reopened.onSensorEvent(300)
        assertEquals(200, reopened.snapshot().remainingSteps)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, reopened.snapshot().state)
    }

    @Test
    fun `large jump after reopening is clamped and cannot complete the challenge`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)

        val reopened = harness.rebuild()
        harness.sensor.setReading(250 + 900_000)
        reopened.onSensorEvent(250 + 900_000)
        assertEquals(250, reopened.snapshot().remainingSteps)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, reopened.snapshot().state)
    }

    @Test
    fun `sensor events after completion are ignored`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)

        harness.sensor.setReading(600)
        harness.engine.onSensorEvent(600)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)
        assertEquals(0, harness.engine.snapshot().remainingSteps)
        assertEquals(500, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `completion stops the alarm sound and never restarts it`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(false, harness.player.isPlaying)

        harness.sensor.setReading(800)
        harness.engine.onSensorEvent(800)
        assertEquals(false, harness.player.isPlaying)
    }
}
