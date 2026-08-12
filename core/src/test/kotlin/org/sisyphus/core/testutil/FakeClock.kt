package org.sisyphus.core.testutil

import org.sisyphus.core.platform.Clock
import java.time.Instant
import java.time.ZoneId

class FakeClock(
    initialMillis: Long = 0L,
    initialZone: ZoneId = ZoneId.of("UTC"),
) : Clock {
    private var currentMillis = initialMillis
    var zoneOverride: ZoneId = initialZone

    override fun currentTimeMillis(): Long = currentMillis

    override fun zone(): ZoneId = zoneOverride

    fun advance(millis: Long) {
        currentMillis += millis
    }

    fun set(millis: Long) {
        currentMillis = millis
    }

    fun setZone(zone: ZoneId) {
        zoneOverride = zone
    }
}

fun isoMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
