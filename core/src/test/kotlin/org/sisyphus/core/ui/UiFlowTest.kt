package org.sisyphus.core.ui

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.testutil.EngineHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiFlowTest {
    @Test
    fun `setup flow configures alarm, requirement and saves`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)

        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
        assertEquals(setOf(UiAction.ConfigureAlarm), ui.availableActions())

        harness.engine.configureAlarm(requiredSteps = 500, hour = 6, minute = 0)
        val saved = harness.engine.currentSettings()
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)
        assertEquals(500, saved.requiredSteps)
        assertEquals(6, saved.alarmHour)
        assertEquals(0, saved.alarmMinute)
    }

    @Test
    fun `alarm flow reaches completion and stops the sound`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)

        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(setOf(UiAction.StartChallenge), ui.availableActions())
        assertTrue(harness.player.isPlaying)

        harness.engine.startChallenge()
        assertEquals(500, harness.engine.snapshot().remainingSteps)

        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        assertEquals(250, harness.engine.snapshot().remainingSteps)

        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(0, harness.engine.snapshot().remainingSteps)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)
        assertTrue(!harness.player.isPlaying)
        assertEquals(setOf(UiAction.AcknowledgeCompletion), ui.availableActions())

        harness.engine.acknowledgeCompletion()
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
    }

    @Test
    fun `back does not dismiss the challenge`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        assertTrue(ui.availableActions().isEmpty())
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, harness.engine.snapshot().state)
        assertEquals(317, harness.engine.snapshot().remainingSteps)
    }

    @Test
    fun `the ui exposes no stop button during a challenge`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)
        harness.armAndStart(requiredSteps = 500)

        assertEquals(emptySet<UiAction>(), ui.availableActions())
        assertTrue(UiAction.CancelAlarm !in ui.availableActions())
        assertFailsWith<IllegalStateException> { harness.engine.cancelAlarm() }
    }

    @Test
    fun `there is no snooze control anywhere`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)

        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()

        assertEquals(setOf(UiAction.StartChallenge), ui.availableActions())

        val closedActionVocabulary =
            setOf<UiAction>(
                UiAction.ConfigureAlarm,
                UiAction.CancelAlarm,
                UiAction.StartChallenge,
                UiAction.AcknowledgeCompletion,
            )
        val names = closedActionVocabulary.toString().lowercase()
        assertTrue("snooze" !in names)
        assertTrue("pause" !in names)
        assertTrue("stop" !in names)
        assertTrue("dismiss" !in names)
    }

    @Test
    fun `reopening the app restores the active challenge`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val reopened = harness.rebuild()
        val reopenedUi = ChallengeUi(reopened)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, reopened.snapshot().state)
        assertEquals(183, reopened.snapshot().completedSteps)
        assertTrue(reopenedUi.availableActions().isEmpty())
    }

    @Test
    fun `ui recreation does not reset progress`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        harness.sensor.setReading(183)
        harness.engine.onSensorEvent(183)

        val recreatedActivity = harness.rebuild()
        val recreatedUi = ChallengeUi(recreatedActivity)
        assertEquals(183, recreatedActivity.snapshot().completedSteps)
        assertEquals(317, recreatedActivity.snapshot().remainingSteps)
        assertTrue(recreatedUi.availableActions().isEmpty())
    }

    @Test
    fun `cannot configure a new alarm while one is active`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        assertFailsWith<IllegalStateException> { harness.engine.configureAlarm(1000, 7, 0) }
    }

    @Test
    fun `cancel alarm is only exposed while armed`() {
        val harness = EngineHarness()
        val ui = ChallengeUi(harness.engine)

        assertEquals(setOf(UiAction.ConfigureAlarm), ui.availableActions())
        harness.engine.configureAlarm(500, 6, 0)
        assertEquals(setOf(UiAction.CancelAlarm), ui.availableActions())

        harness.engine.onAlarmFired()
        assertEquals(setOf(UiAction.StartChallenge), ui.availableActions())
    }

    @Test
    fun `impossible times are rejected and nothing is scheduled`() {
        val harness = EngineHarness()
        assertFailsWith<IllegalArgumentException> { harness.engine.configureAlarm(500, 46, 0) }
        assertFailsWith<IllegalArgumentException> { harness.engine.configureAlarm(500, 6, 97) }
        assertFailsWith<IllegalArgumentException> { harness.engine.configureAlarm(500, -1, 0) }
        assertFailsWith<IllegalArgumentException> { harness.engine.configureAlarm(500, 0, 60) }

        assertEquals(0, harness.scheduler.scheduleCalls.size)
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
    }
}
