package org.sisyphus.android.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationSupport {
    fun areEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        return manager.areNotificationsEnabled()
    }
}

object SensorSupport {
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        return manager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null ||
            manager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_DETECTOR) != null
    }
}

object ExactAlarmSupport {
    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }
}

object FullScreenIntentSupport {
    fun canUse(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        return manager.canUseFullScreenIntent()
    }
}

object PermissionFlow {
    const val KEY_NOTIFICATIONS_REQUESTED = "permissions.notificationsRequested"

    fun notificationsAlreadyRequested(context: Context): Boolean {
        val store =
            SharedPrefsKeyValueStore(
                context.getSharedPreferences(SharedPrefsKeyValueStore.PREFS_NAME, Context.MODE_PRIVATE),
            )
        return store.getBoolean(KEY_NOTIFICATIONS_REQUESTED) ?: false
    }

    fun markNotificationsRequested(context: Context) {
        val store =
            SharedPrefsKeyValueStore(
                context.getSharedPreferences(SharedPrefsKeyValueStore.PREFS_NAME, Context.MODE_PRIVATE),
            )
        store.putBoolean(KEY_NOTIFICATIONS_REQUESTED, true)
    }
}
