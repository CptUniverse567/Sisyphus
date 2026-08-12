package org.sisyphus.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.sisyphus.android.SisyphusApp
import org.sisyphus.core.challenge.ChallengeState

class SisyphusService : Service() {
    private val app get() = application as SisyphusApp
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_ALARM_FIRED -> fireAlarm()
            ACTION_START_CHALLENGE -> {
                ensureForeground()
                startSensorIfChallengeActive()
            }
            ACTION_START_SENSOR -> {
                ensureForeground()
                startSensorIfChallengeActive()
            }
            ACTION_STOP_SENSOR -> {
                app.graph.sensor.unregister()
                app.graph.publishState()
            }
            ACTION_RESUME -> {
                ensureForeground()
                resumeIfRunning()
            }
            else -> {
                ensureForeground()
                resumeIfRunning()
            }
        }
        return START_STICKY
    }

    private fun fireAlarm() {
        val graph = app.graph
        when (graph.engine.snapshot().state) {
            ChallengeState.ARMED -> graph.engine.onAlarmFired()
            ChallengeState.RINGING, ChallengeState.CHALLENGE_ACTIVE ->
                graph.engine.resumeChallengeSound()
            else -> {
                Log.w(TAG, "alarm broadcast ignored, state=${graph.engine.snapshot().state}")
                return
            }
        }
        graph.publishState()
        Log.d(TAG, "alarm fired, state=${graph.engine.snapshot().state}")
    }

    private fun resumeIfRunning() {
        val graph = app.graph
        if (graph.engine.snapshot().isChallengeRunning) {
            graph.engine.resumeChallengeSound()
            startSensorIfChallengeActive()
            graph.publishState()
        }
    }

    private fun startSensorIfChallengeActive() {
        val graph = app.graph
        if (graph.engine.snapshot().state != ChallengeState.CHALLENGE_ACTIVE) {
            Log.d(TAG, "sensor not started, state=${graph.engine.snapshot().state}")
            return
        }
        graph.sensor.register { reading -> onStep(reading) }
    }

    private fun onStep(reading: Long) {
        val graph = app.graph
        val before = graph.engine.snapshot().remainingSteps
        graph.engine.onSensorEvent(reading)
        graph.publishState()
        val after = graph.engine.snapshot()
        Log.d(TAG, "baseline handled; remaining $before -> ${after.remainingSteps} (state=${after.state})")
        if (after.state == ChallengeState.COMPLETED) {
            graph.sensor.unregister()
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        runCatching {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            foregroundStarted = true
        }.onFailure {
            Log.e(TAG, "startForeground failed: ${it.message}")
        }
    }

    override fun onDestroy() {
        app.graph.sensor.unregister()
        super.onDestroy()
    }

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(FOREGROUND_CHANNEL, "Challenge service", NotificationManager.IMPORTANCE_LOW),
        )
        val state = app.graph.engine.snapshot()
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sisyphus")
            .setContentText("${state.remainingSteps} steps remaining to silence the alarm.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    companion object {
        const val ACTION_ALARM_FIRED = "org.sisyphus.android.action.ALARM_FIRED"
        const val ACTION_START_CHALLENGE = "org.sisyphus.android.action.START_CHALLENGE"
        const val ACTION_START_SENSOR = "org.sisyphus.android.action.START_SENSOR"
        const val ACTION_STOP_SENSOR = "org.sisyphus.android.action.STOP_SENSOR"
        const val ACTION_RESUME = "org.sisyphus.android.action.RESUME"
        const val NOTIFICATION_ID = 1
        const val FOREGROUND_CHANNEL = "sisyphus_service"

        private const val TAG = "SisyphusService"
    }
}
