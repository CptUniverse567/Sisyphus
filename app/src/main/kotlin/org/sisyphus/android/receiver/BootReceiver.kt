package org.sisyphus.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.sisyphus.android.SisyphusApp
import org.sisyphus.android.service.SisyphusService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as SisyphusApp
        val engine = app.graph.engine

        engine.restoreAlarmOnBoot()
        app.graph.publishState()

        if (engine.snapshot().isChallengeRunning) {
            val serviceIntent =
                Intent(context, SisyphusService::class.java).apply {
                    action = SisyphusService.ACTION_RESUME
                }
            context.startForegroundService(serviceIntent)
        }
    }
}
