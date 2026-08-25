package org.sisyphus.core.alarm

import org.sisyphus.core.testutil.FakeClock
import org.sisyphus.core.testutil.isoMillis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmRecurrenceCalculatorTest {
    // Friday 2026-01-02 05:00 UTC
    private val clock = FakeClock(isoMillis("2026-01-02T05:00:00Z"), ZoneId.of("UTC"))
    private val calculator = AlarmRecurrenceCalculator(clock)

    private fun alarm(
        repeat: RepeatMode,
        hour: Int = 6,
        minute: Int = 0,
        customDays: Set<DayOfWeek> = emptySet(),
        onceDate: LocalDate? = null,
    ) = Alarm(
        id = "a1",
        hour = hour,
        minute = minute,
        repeatMode = repeat,
        customDays = customDays,
        onceDate = onceDate,
    )

    @Test
    fun `daily fires today when time is still ahead`() {
        val a = alarm(RepeatMode.DAILY)
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `daily rolls to tomorrow when time has passed`() {
        val a = alarm(RepeatMode.DAILY, hour = 4)
        assertEquals(isoMillis("2026-01-03T04:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `daily rolls to tomorrow when now is exactly at the hour`() {
        clock.set(isoMillis("2026-01-02T06:00:00Z"))
        val a = alarm(RepeatMode.DAILY, hour = 6)
        assertEquals(isoMillis("2026-01-03T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekdays on friday after the time rolls to monday`() {
        // Friday 07:00 (after the 06:00 alarm) -> next weekday is Monday 2026-01-05
        clock.set(isoMillis("2026-01-02T07:00:00Z"))
        val a = alarm(RepeatMode.WEEKDAYS)
        assertEquals(isoMillis("2026-01-05T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekdays on friday before the time fires today`() {
        // Friday 05:00 with a 06:00 alarm -> fires today because Friday is a weekday
        clock.set(isoMillis("2026-01-02T05:00:00Z"))
        val a = alarm(RepeatMode.WEEKDAYS)
        assertEquals(isoMillis("2026-01-02T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekdays on monday morning fires today`() {
        clock.set(isoMillis("2026-01-05T05:00:00Z")) // Monday
        val a = alarm(RepeatMode.WEEKDAYS)
        assertEquals(isoMillis("2026-01-05T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekdays saturday rolls to monday`() {
        clock.set(isoMillis("2026-01-03T05:00:00Z")) // Saturday
        val a = alarm(RepeatMode.WEEKDAYS)
        assertEquals(isoMillis("2026-01-05T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekends on friday rolls to saturday`() {
        val a = alarm(RepeatMode.WEEKENDS)
        assertEquals(isoMillis("2026-01-03T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `weekends on saturday before time fires today`() {
        clock.set(isoMillis("2026-01-03T05:00:00Z")) // Saturday
        val a = alarm(RepeatMode.WEEKENDS)
        assertEquals(isoMillis("2026-01-03T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `custom monday wednesday friday on tuesday rolls to wednesday`() {
        clock.set(isoMillis("2026-01-06T05:00:00Z")) // Tuesday
        val a =
            alarm(
                RepeatMode.CUSTOM,
                customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        assertEquals(isoMillis("2026-01-07T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `custom single day rolls across a week`() {
        clock.set(isoMillis("2026-01-02T05:00:00Z")) // Friday
        val a = alarm(RepeatMode.CUSTOM, customDays = setOf(DayOfWeek.THURSDAY))
        assertEquals(isoMillis("2026-01-08T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `once fires on its configured date when future`() {
        clock.set(isoMillis("2026-01-02T05:00:00Z"))
        val a = alarm(RepeatMode.ONCE, hour = 8, minute = 30, onceDate = LocalDate.of(2026, 1, 5))
        assertEquals(isoMillis("2026-01-05T08:30:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `once is missed and not rescheduled when its date has passed`() {
        clock.set(isoMillis("2026-01-02T05:00:00Z"))
        val a = alarm(RepeatMode.ONCE, hour = 8, onceDate = LocalDate.of(2026, 1, 1))
        assertNull(calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `once on today before time fires today`() {
        clock.set(isoMillis("2026-01-02T05:00:00Z"))
        val a = alarm(RepeatMode.ONCE, hour = 8, onceDate = LocalDate.of(2026, 1, 2))
        assertEquals(isoMillis("2026-01-02T08:00:00Z"), calculator.nextOccurrenceMillis(a))
    }

    @Test
    fun `custom schedule on matching day after time rolls to next matching day`() {
        clock.set(isoMillis("2026-01-02T07:00:00Z")) // Friday, after 06:00
        val a = alarm(RepeatMode.CUSTOM, customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(isoMillis("2026-01-05T06:00:00Z"), calculator.nextOccurrenceMillis(a))
    }
}
