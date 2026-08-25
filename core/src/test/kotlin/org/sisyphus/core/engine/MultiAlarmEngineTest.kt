package org.sisyphus.core.engine

import org.sisyphus.core.alarm.AlarmSpec
import org.sisyphus.core.alarm.RepeatMode
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.testutil.EngineHarness
import org.sisyphus.core.testutil.isoMillis
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiAlarmEngineTest {
    private val spec =
        AlarmSpec(
            hour = 6,
            minute = 0,
            repeatMode = RepeatMode.DAILY,
            requiredSteps = 500,
        )

    @Test
    fun `multiple alarms coexist independently`() {
        val harness = EngineHarness()
        harness.engine.addAlarm(spec)
        harness.engine.addAlarm(spec.copy(hour = 7, requiredSteps = 1000, repeatMode = RepeatMode.WEEKENDS))

        val alarms = harness.engine.alarms()
        assertEquals(2, alarms.size)
        assertEquals(500, alarms[0].requiredSteps)
        assertEquals(1000, alarms[1].requiredSteps)
        assertEquals(RepeatMode.WEEKENDS, alarms[1].repeatMode)
        assertTrue(alarms.map { it.id }.toSet().size == 2)
    }

    @Test
    fun `each enabled alarm is scheduled under its own tag`() {
        val harness = EngineHarness()
        val a = harness.engine.addAlarm(spec)
        val b = harness.engine.addAlarm(spec.copy(hour = 7, requiredSteps = 1000))
        assertTrue(harness.scheduler.isScheduled(a[0].id))
        assertTrue(harness.scheduler.isScheduled(b[1].id))
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), harness.scheduler.pendingFireAtFor(a[0].id))
        assertEquals(isoMillis("2026-01-01T07:00:00Z"), harness.scheduler.pendingFireAtFor(b[1].id))
    }

    @Test
    fun `disabled alarm is not scheduled and re-enabling reschedules`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec)
        val id = added[0].id

        harness.engine.setAlarmEnabled(id, false)
        assertFalse(harness.scheduler.isScheduled(id))
        assertFalse(harness.engine.alarms().first { it.id == id }.enabled)

        harness.engine.setAlarmEnabled(id, true)
        assertTrue(harness.scheduler.isScheduled(id))
        assertTrue(harness.engine.alarms().first { it.id == id }.enabled)
    }

    @Test
    fun `editing reschedules the alarm immediately`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec)
        val id = added[0].id
        harness.engine.updateAlarm(id, spec.copy(hour = 9, minute = 15))
        assertEquals(isoMillis("2026-01-01T09:15:00Z"), harness.scheduler.pendingFireAtFor(id))
        val updated = harness.engine.alarms().first { it.id == id }
        assertEquals(9, updated.hour)
        assertEquals(15, updated.minute)
    }

    @Test
    fun `deleting removes schedule and alarm, other alarms unaffected`() {
        val harness = EngineHarness()
        val a = harness.engine.addAlarm(spec)
        val b = harness.engine.addAlarm(spec.copy(hour = 8))
        val idA = a[0].id
        val idB = b[1].id

        harness.engine.deleteAlarm(idA)
        assertFalse(harness.engine.alarms().any { it.id == idA })
        assertEquals(1, harness.engine.alarms().size)
        assertFalse(harness.scheduler.isScheduled(idA))
        assertTrue(harness.scheduler.isScheduled(idB))
    }

    @Test
    fun `firing a specific alarm creates a fresh challenge with its steps and sound`() {
        val harness = EngineHarness()
        val added =
            harness.engine.addAlarm(
                spec.copy(requiredSteps = 1000, soundSelection = SoundSelection.CustomFile("content://x/a.ogg")),
            )
        val id = added[0].id

        harness.engine.onAlarmFired(id)
        val s = harness.engine.snapshot()
        assertEquals(ChallengeState.RINGING, s.state)
        assertEquals(1000, s.requiredSteps)
        assertEquals(id, s.alarmId)
        assertTrue(harness.player.isPlaying)
    }

    @Test
    fun `a disabled alarm does not fire`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec)
        val id = added[0].id
        harness.engine.setAlarmEnabled(id, false)

        harness.engine.onAlarmFired(id)
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
    }

    @Test
    fun `a second alarm firing while one is active is skipped`() {
        val harness = EngineHarness()
        val a = harness.engine.addAlarm(spec)
        val b = harness.engine.addAlarm(spec.copy(hour = 8))
        harness.engine.onAlarmFired(a[0].id)
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)

        harness.engine.onAlarmFired(b[1].id)
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertEquals(a[0].id, harness.engine.snapshot().alarmId)
    }

    @Test
    fun `challenge progress applies only to the firing alarm and never carries to the next occurrence`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec) // 500 steps daily
        val id = added[0].id

        harness.engine.onAlarmFired(id)
        harness.engine.startChallenge()
        harness.sensor.setReading(200)
        harness.engine.onSensorEvent(200)
        assertEquals(300, harness.engine.snapshot().remainingSteps)

        // Complete and acknowledge -> fresh IDLE
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)
        harness.engine.acknowledgeCompletion()
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)

        // Next occurrence must start fresh with the full requirement and zero progress.
        harness.engine.onAlarmFired(id)
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertEquals(500, harness.engine.snapshot().requiredSteps)
        assertEquals(0, harness.engine.snapshot().completedSteps)
    }

    @Test
    fun `firing one alarm does not disturb another alarm's schedule`() {
        val harness = EngineHarness()
        val a = harness.engine.addAlarm(spec)
        val b = harness.engine.addAlarm(spec.copy(hour = 8))
        val idA = a[0].id
        val idB = b[1].id

        harness.engine.onAlarmFired(idA)
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertTrue(harness.scheduler.isScheduled(idB), "other alarm remains scheduled")
        assertEquals(isoMillis("2026-01-01T08:00:00Z"), harness.scheduler.pendingFireAtFor(idB))
    }

    @Test
    fun `reboot recovery reschedules all enabled alarms`() {
        val harness = EngineHarness()
        harness.engine.addAlarm(spec) // daily 06:00
        harness.engine.addAlarm(spec.copy(hour = 8, requiredSteps = 700)) // daily 08:00

        harness.scheduler.reset()
        harness.clock.set(isoMillis("2026-01-01T07:00:00Z"))

        val rebuilt = harness.rebuild()
        rebuilt.restoreAlarmOnBoot()

        // 06:00 daily is now missed -> rescheduled to tomorrow 06:00
        // 08:00 daily still ahead today -> today 08:00
        assertEquals(2, rebuilt.alarms().size)
        assertTrue(harness.scheduler.scheduledTags().size == 2)
        assertEquals(ChallengeState.IDLE, rebuilt.snapshot().state)
    }

    @Test
    fun `missed daily occurrence is skipped never fired late`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec) // daily 06:00
        val id = added[0].id

        harness.scheduler.reset()
        // Pretend we rebooted at 09:00; 06:00 today is missed.
        harness.clock.set(isoMillis("2026-01-01T09:00:00Z"))
        harness.engine.restoreAlarmOnBoot()

        // Next occurrence is tomorrow 06:00, not a late fire today.
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), harness.scheduler.pendingFireAtFor(id))
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
    }

    @Test
    fun `missed once alarm is not rescheduled`() {
        val harness = EngineHarness()
        val added =
            harness.engine.addAlarm(
                spec.copy(
                    repeatMode = RepeatMode.ONCE,
                    onceDate = LocalDate.of(2026, 1, 1),
                    hour = 6,
                ),
            )
        val id = added[0].id

        harness.scheduler.reset()
        harness.clock.set(isoMillis("2026-01-01T09:00:00Z"))
        harness.engine.restoreAlarmOnBoot()

        assertFalse(harness.scheduler.isScheduled(id))
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
    }

    @Test
    fun `alarm list and challenge survive process death`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec.copy(requiredSteps = 750))
        harness.engine.addAlarm(spec.copy(hour = 8, repeatMode = RepeatMode.WEEKDAYS))
        harness.engine.onAlarmFired(added[0].id)
        harness.engine.startChallenge()
        harness.sensor.setReading(150)
        harness.engine.onSensorEvent(150)

        val rebuilt = harness.rebuild()
        assertEquals(2, rebuilt.alarms().size)
        val challenge = rebuilt.snapshot()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, challenge.state)
        assertEquals(150, challenge.completedSteps)
        assertEquals(added[0].id, challenge.alarmId)
    }

    @Test
    fun `deleting the active alarm clears its challenge`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec)
        val id = added[0].id
        harness.engine.onAlarmFired(id)
        harness.engine.startChallenge()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, harness.engine.snapshot().state)

        harness.engine.deleteAlarm(id)
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
        assertFalse(harness.player.isPlaying)
    }

    @Test
    fun `editing an in-progress alarm does not corrupt the challenge`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec)
        val id = added[0].id
        harness.engine.onAlarmFired(id)
        harness.engine.startChallenge()
        harness.sensor.setReading(200)
        harness.engine.onSensorEvent(200)

        harness.engine.updateAlarm(id, spec.copy(hour = 10, requiredSteps = 50))

        val s = harness.engine.snapshot()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, s.state)
        assertEquals(300, s.remainingSteps, "in-progress challenge steps are not overwritten by edit")
    }

    @Test
    fun `custom days produce a valid next occurrence`() {
        val harness = EngineHarness()
        val added =
            harness.engine.addAlarm(
                spec.copy(repeatMode = RepeatMode.CUSTOM, customDays = setOf(DayOfWeek.MONDAY)),
            )
        val id = added[0].id
        // 2026-01-01 is Thursday; next Monday is 2026-01-05
        assertEquals(isoMillis("2026-01-05T06:00:00Z"), harness.scheduler.pendingFireAtFor(id))
    }

    @Test
    fun `legacy primary alarm still arms and fires via the v1 API`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)
        harness.engine.onAlarmFired()
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertEquals(1, harness.engine.alarms().size)
        assertNotNull(harness.engine.alarms().firstOrNull { it.id == SisyphusEngine.LEGACY_ALARM_ID })
    }

    @Test
    fun `a recurring alarm reschedules its next occurrence after firing`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec) // daily 06:00
        val id = added[0].id

        harness.clock.set(isoMillis("2026-01-01T06:00:00Z"))
        harness.engine.onAlarmFired(id)
        // Next daily occurrence is tomorrow 06:00.
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), harness.scheduler.pendingFireAtFor(id))
    }

    @Test
    fun `a one-shot alarm is not rescheduled after firing`() {
        val harness = EngineHarness()
        val added =
            harness.engine.addAlarm(
                spec.copy(
                    repeatMode = RepeatMode.ONCE,
                    onceDate = LocalDate.of(2026, 1, 1),
                    hour = 4,
                ),
            )
        val id = added[0].id
        harness.engine.onAlarmFired(id)
        assertFalse(harness.scheduler.isScheduled(id))
    }

    @Test
    fun `acknowledging leaves the next occurrence scheduled for a recurring alarm`() {
        val harness = EngineHarness()
        val added = harness.engine.addAlarm(spec) // daily 06:00
        val id = added[0].id
        harness.clock.set(isoMillis("2026-01-01T06:00:00Z"))
        harness.engine.onAlarmFired(id)
        harness.engine.startChallenge()
        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)
        harness.engine.acknowledgeCompletion()
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), harness.scheduler.pendingFireAtFor(id))
    }
}
