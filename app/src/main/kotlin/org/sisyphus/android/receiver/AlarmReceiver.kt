package org.sisyphus.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.sisyphus.android.service.SisyphusService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val serviceIntent =
            Intent(context, SisyphusService::class.java).apply {
                action = SisyphusService.ACTION_ALARM_FIRED
            }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
