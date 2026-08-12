package org.sisyphus.core.testutil

import org.sisyphus.core.platform.AlarmScheduler

class FakeAlarmScheduler : AlarmScheduler {
    val scheduleCalls = mutableListOf<Long>()
    val cancelCalls = mutableListOf<String>()
    var pendingFireAt: Long? = null

    override fun schedule(
        fireAtMillis: Long,
        tag: String,
    ) {
        scheduleCalls.add(fireAtMillis)
        pendingFireAt = fireAtMillis
    }

    override fun cancel(tag: String) {
        cancelCalls.add(tag)
        pendingFireAt = null
    }

    override fun pendingFireAtMillis(): Long? = pendingFireAt

    fun reset() {
        scheduleCalls.clear()
        cancelCalls.clear()
        pendingFireAt = null
    }
}
