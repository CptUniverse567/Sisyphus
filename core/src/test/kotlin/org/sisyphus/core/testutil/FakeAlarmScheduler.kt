package org.sisyphus.core.testutil

import org.sisyphus.core.engine.SisyphusEngine
import org.sisyphus.core.platform.AlarmScheduler

class FakeAlarmScheduler : AlarmScheduler {
    private val scheduled = mutableMapOf<String, Long>()
    val scheduleCalls = mutableListOf<Long>()
    val cancelCalls = mutableListOf<String>()
    val tagsScheduled = mutableListOf<String>()

    override fun schedule(
        fireAtMillis: Long,
        tag: String,
    ) {
        scheduled[tag] = fireAtMillis
        scheduleCalls.add(fireAtMillis)
        tagsScheduled.add(tag)
    }

    override fun cancel(tag: String) {
        scheduled.remove(tag)
        cancelCalls.add(tag)
    }

    /** Legacy accessor: returns the primary (legacy) alarm's pending fire time, else the most recent. */
    override fun pendingFireAtMillis(): Long? =
        scheduled[SisyphusEngine.LEGACY_ALARM_ID] ?: scheduled.values.lastOrNull()

    fun pendingFireAtFor(tag: String): Long? = scheduled[tag]

    fun isScheduled(tag: String): Boolean = scheduled.containsKey(tag)

    fun scheduledTags(): Set<String> = scheduled.keys

    fun reset() {
        scheduled.clear()
        scheduleCalls.clear()
        cancelCalls.clear()
        tagsScheduled.clear()
    }
}
