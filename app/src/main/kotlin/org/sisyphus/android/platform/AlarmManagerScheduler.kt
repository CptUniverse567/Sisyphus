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
        store.putLong(KEY_NEXT_FIRE, fireAtMillis)
    }

    override fun cancel(tag: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(tag))
        store.remove(KEY_NEXT_FIRE)
    }

    override fun pendingFireAtMillis(): Long? = store.getLong(KEY_NEXT_FIRE)

    private fun pendingIntent(tag: String): PendingIntent {
        val intent =
            Intent(context, AlarmReceiver::class.java).apply {
                action = "org.sisyphus.android.ALARM"
                putExtra("tag", tag)
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val REQUEST_CODE = 1001
        const val KEY_NEXT_FIRE = "alarm.nextFire"
    }
}
