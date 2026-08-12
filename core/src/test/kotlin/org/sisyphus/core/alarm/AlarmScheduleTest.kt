package org.sisyphus.core.alarm

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.testutil.EngineHarness
import org.sisyphus.core.testutil.FakeClock
import org.sisyphus.core.testutil.isoMillis
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmTimeCalculationTest {
    private val clock = FakeClock(isoMillis("2026-01-01T05:00:00Z"), ZoneId.of("UTC"))
    private val calculator = AlarmTimeCalculator(clock)

    @Test
    fun `trigger time is today when the time is still ahead`() {
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), calculator.nextTriggerMillis(6, 0))
    }

    @Test
    fun `trigger time is tomorrow when the time has already passed`() {
        assertEquals(isoMillis("2026-01-02T04:00:00Z"), calculator.nextTriggerMillis(4, 0))
    }

    @Test
    fun `trigger time is tomorrow when the time has just passed`() {
        assertEquals(isoMillis("2026-01-02T04:59:00Z"), calculator.nextTriggerMillis(4, 59))
    }

    @Test
    fun `trigger time is tomorrow when now is exactly at the hour`() {
        clock.set(isoMillis("2026-01-01T06:00:00Z"))
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), calculator.nextTriggerMillis(6, 0))
    }

    @Test
    fun `fire delay is deterministic without waiting`() {
        val delay = calculator.fireDelayMillis(6, 0)
        assertEquals(3_600_000L, delay)
    }

    @Test
    fun `midnight boundary rolls to the next day`() {
        clock.set(isoMillis("2026-01-01T23:59:59Z"))
        assertEquals(isoMillis("2026-01-02T00:01:00Z"), calculator.nextTriggerMillis(0, 1))
    }

    @Test
    fun `invalid hour and minute are rejected`() {
        assertFailsWith<IllegalArgumentException> { calculator.nextTriggerMillis(24, 0) }
        assertFailsWith<IllegalArgumentException> { calculator.nextTriggerMillis(-1, 0) }
        assertFailsWith<IllegalArgumentException> { calculator.nextTriggerMillis(6, 60) }
    }

    @Test
    fun `trigger time honours the configured time zone`() {
        val jstClock = FakeClock(isoMillis("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Tokyo"))
        val jstCalculator = AlarmTimeCalculator(jstClock)
        assertEquals(isoMillis("2026-01-01T01:00:00Z"), jstCalculator.nextTriggerMillis(10, 0))
        assertEquals(isoMillis("2026-01-01T23:00:00Z"), jstCalculator.nextTriggerMillis(8, 0))
    }

    @Test
    fun `fixed offset zones are honoured`() {
        val offsetClock = FakeClock(isoMillis("2026-01-01T00:00:00Z"), ZoneOffset.ofHours(5))
        val offsetCalculator = AlarmTimeCalculator(offsetClock)
        assertEquals(isoMillis("2026-01-01T01:00:00Z"), offsetCalculator.nextTriggerMillis(6, 0))
    }

    @Test
    fun `changing the zone changes the computed trigger time deterministically`() {
        clock.set(isoMillis("2026-01-01T05:00:00Z"))
        val utc = calculator.nextTriggerMillis(6, 0)
        clock.setZone(ZoneId.of("America/New_York"))
        val ny = calculator.nextTriggerMillis(6, 0)
        assertTrue(utc != ny)
        assertEquals(isoMillis("2026-01-01T11:00:00Z"), ny)
    }
}

class AlarmScheduleManagerTest {
    @Test
    fun `alarm creation schedules the computed time`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        val fireAt = manager.schedule(6, 0)
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), fireAt)
        assertEquals(fireAt, harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `alarm cancellation clears the pending alarm`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        manager.schedule(6, 0)
        manager.cancel()
        assertNull(harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `alarm replacement keeps exactly one pending alarm with the latest time`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        val first = manager.schedule(6, 0)
        val second = manager.schedule(7, 0)
        assertEquals(first, isoMillis("2026-01-01T06:00:00Z"))
        assertEquals(second, isoMillis("2026-01-01T07:00:00Z"))
        assertEquals(second, harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `duplicate alarm creation is prevented`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        manager.schedule(6, 0)
        manager.schedule(6, 0)
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `restore with a future fire time reschedules without firing`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        val fireAt = isoMillis("2026-01-01T06:00:00Z")
        val result = manager.restoreOrTrigger(fireAt, harness.clock.currentTimeMillis())
        assertEquals(AlarmRestoreResult.RESCHEDULED, result)
        assertEquals(fireAt, harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `restore with a past fire time schedules an immediate fire`() {
        val harness = EngineHarness()
        val manager = AlarmScheduleManager(harness.scheduler, AlarmTimeCalculator(harness.clock))
        val fireAt = isoMillis("2026-01-01T04:00:00Z")
        val result = manager.restoreOrTrigger(fireAt, harness.clock.currentTimeMillis())
        assertEquals(AlarmRestoreResult.DUE, result)
        assertEquals(harness.clock.currentTimeMillis(), harness.scheduler.pendingFireAtMillis())
    }
}

class EngineAlarmSchedulingTest {
    @Test
    fun `arming schedules the alarm and persists the fire time`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), harness.scheduler.pendingFireAtMillis())
        assertEquals(ChallengeState.ARMED, harness.engine.snapshot().state)
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), harness.engine.snapshot().alarmFireTimeMillis)
    }

    @Test
    fun `cancelling the alarm clears the schedule and returns to idle`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.cancelAlarm()
        assertEquals(ChallengeState.IDLE, harness.engine.snapshot().state)
        assertNull(harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `reboot recovery reschedules a future alarm without firing`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.scheduler.reset()
        harness.clock.set(isoMillis("2026-01-01T05:30:00Z"))

        val rebuilt = harness.rebuild()
        val result = rebuilt.restoreAlarmOnBoot()
        assertEquals(ChallengeState.ARMED, result.state)
        assertEquals(isoMillis("2026-01-01T06:00:00Z"), harness.scheduler.pendingFireAtMillis())
    }

    @Test
    fun `reboot recovery after the fire time triggers the alarm immediately`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.scheduler.reset()
        harness.clock.set(isoMillis("2026-01-01T06:30:00Z"))

        val rebuilt = harness.rebuild()
        val result = rebuilt.restoreAlarmOnBoot()
        assertEquals(ChallengeState.RINGING, result.state)
        assertTrue(harness.player.isPlaying)
    }

    @Test
    fun `alarm can fire when the activity is not open`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.clock.set(isoMillis("2026-01-01T06:00:00Z"))
        harness.engine.onAlarmFired()
        assertEquals(ChallengeState.RINGING, harness.engine.snapshot().state)
        assertTrue(harness.player.isPlaying)
        assertEquals(1, harness.notifications.shown.size)
    }

    @Test
    fun `reconfiguring while armed is rejected`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        assertFailsWith<IllegalStateException> { harness.engine.configureAlarm(1000, 7, 0) }
    }
}
