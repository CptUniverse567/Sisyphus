package org.sisyphus.android.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.sisyphus.android.receiver.AlarmReceiver
import org.sisyphus.core.platform.AlarmScheduler

class AlarmManagerScheduler(
    private val context: Context,
    private val store: SharedPrefsKeyValueStore,
) : AlarmScheduler {
    private val pendingByTag = mutableMapOf<String, Long>()
    private var lastTag: String? = null

    override fun schedule(
        fireAtMillis: Long,
        tag: String,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(tag)
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() ->
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ->
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, pending), pending)

                else ->
                    alarmManager.set(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)
            }
        }.onFailure {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pending)
        }
        pendingByTag[tag] = fireAtMillis
        lastTag = tag
        store.putLong(keyFor(tag), fireAtMillis)
    }

    override fun cancel(tag: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(tag))
        pendingByTag.remove(tag)
        if (lastTag == tag) lastTag = null
        store.remove(keyFor(tag))
    }

    override fun pendingFireAtMillis(): Long? = lastTag?.let { pendingByTag[it] }

    private fun keyFor(tag: String) = "$KEY_NEXT_FIRE.$tag"

    private fun pendingIntent(tag: String): PendingIntent {
        val intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = "org.sisyphus.android.ALARM"
                putExtra("tag", tag)
                putExtra("alarmId", tag)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(tag),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(tag: String): Int {
        // Stable, distinct request code per alarm tag so multiple alarms can coexist.
        val base = REQUEST_CODE_BASE + (tag.hashCode() % REQUEST_CODE_SPAN)
        return if (base < 0) base + REQUEST_CODE_SPAN else base
    }

    companion object {
        const val REQUEST_CODE_BASE = 1001
        const val REQUEST_CODE_SPAN = 1_000_000
        const val KEY_NEXT_FIRE = "alarm.nextFire"
    }
}
