package org.sisyphus.core.alarm

enum class AlarmRestoreResult {
    RESCHEDULED,
    DUE,
}

class AlarmScheduleManager(
    private val scheduler: org.sisyphus.core.platform.AlarmScheduler,
    private val calculator: AlarmTimeCalculator,
) {
    private val tag = "sisyphus_alarm"

    fun peekFireTime(
        hour: Int,
        minute: Int,
    ): Long = calculator.nextTriggerMillis(hour, minute)

    fun schedule(
        hour: Int,
        minute: Int,
    ): Long {
        scheduler.cancel(tag)
        val fireAt = calculator.nextTriggerMillis(hour, minute)
        scheduler.schedule(fireAt, tag)
        return fireAt
    }

    fun scheduleAt(fireAtMillis: Long) {
        scheduler.cancel(tag)
        scheduler.schedule(fireAtMillis, tag)
    }

    fun cancel() {
        scheduler.cancel(tag)
    }

    fun restoreOrTrigger(
        fireAtMillis: Long,
        nowMillis: Long,
    ): AlarmRestoreResult {
        return if (fireAtMillis > nowMillis) {
            scheduleAt(fireAtMillis)
            AlarmRestoreResult.RESCHEDULED
        } else {
            scheduler.cancel(tag)
            scheduler.schedule(nowMillis, tag)
            AlarmRestoreResult.DUE
        }
    }
}
