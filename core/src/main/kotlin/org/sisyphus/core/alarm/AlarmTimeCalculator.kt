package org.sisyphus.core.alarm

import org.sisyphus.core.platform.Clock
import java.time.Instant
import java.time.ZonedDateTime

class AlarmTimeCalculator(private val clock: Clock) {
    fun nextTriggerMillis(
        hour: Int,
        minute: Int,
    ): Long {
        require(hour in 0..23) { "hour must be in 0..23 but was $hour" }
        require(minute in 0..59) { "minute must be in 0..59 but was $minute" }
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.currentTimeMillis()), clock.zone())
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun fireDelayMillis(
        hour: Int,
        minute: Int,
    ): Long = nextTriggerMillis(hour, minute) - clock.currentTimeMillis()
}
