package org.sisyphus.core.alarm

import org.sisyphus.core.platform.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Computes the next future occurrence for an [Alarm] based on its repeat mode and the current
 * time/zone (from the injectable [Clock] for deterministic testing).
 */
class AlarmRecurrenceCalculator(private val clock: Clock) {
    /**
     * Returns the epoch-millis of the next occurrence of [alarm] strictly after now, or null if
     * there is no future occurrence (e.g. a missed ONCE alarm).
     */
    fun nextOccurrenceMillis(alarm: Alarm): Long? = nextOccurrenceMillis(alarm, clock.currentTimeMillis(), clock.zone())

    fun nextOccurrenceMillis(
        alarm: Alarm,
        nowMillis: Long,
        zone: java.time.ZoneId,
    ): Long? {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)

        return when (alarm.repeatMode) {
            RepeatMode.ONCE -> {
                val date = alarm.onceDate ?: return null
                val target = ZonedDateTime.of(date, time, zone)
                if (target.toInstant().toEpochMilli() > nowMillis) target.toInstant().toEpochMilli() else null
            }
            RepeatMode.DAILY -> nextOnOrAfter(now, time, ALL_DAYS)
            RepeatMode.WEEKDAYS -> nextOnOrAfter(now, time, WEEKDAYS)
            RepeatMode.WEEKENDS -> nextOnOrAfter(now, time, WEEKENDS)
            RepeatMode.CUSTOM -> nextOnOrAfter(now, time, alarm.customDays)
        }
    }

    /**
     * Finds the next occurrence on or strictly after [now] where the day-of-week is in [allowedDays]
     * and the time-of-day is [time]. The occurrence is strictly in the future (must be after now).
     */
    private fun nextOnOrAfter(
        now: ZonedDateTime,
        time: LocalTime,
        allowedDays: Set<DayOfWeek>,
    ): Long {
        var candidate = now.toLocalDate()
        for (offset in 0..7) {
            val day = candidate.plusDays(offset.toLong())
            if (day.dayOfWeek in allowedDays) {
                val at = ZonedDateTime.of(day, time, now.zone)
                if (at.toInstant().toEpochMilli() > now.toInstant().toEpochMilli()) {
                    return at.toInstant().toEpochMilli()
                }
            }
        }
        // Unreachable with a 7-day lookahead and a non-empty allowed set.
        throw IllegalStateException("no occurrence found in the next 7 days")
    }

    companion object {
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val WEEKDAYS: Set<DayOfWeek> =
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKENDS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }
}

/** Returns [date] if its [DayOfWeek] is in [days], else the next date in [days] at the same day boundary. */
fun LocalDate.nextMatching(days: Set<DayOfWeek>): LocalDate {
    var d = this
    while (d.dayOfWeek !in days) {
        d = d.plusDays(1)
    }
    return d
}
