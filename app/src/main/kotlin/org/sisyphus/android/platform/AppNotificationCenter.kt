package org.sisyphus.android.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.sisyphus.android.ui.MainActivity
import org.sisyphus.core.platform.NotificationCenter

class AppNotificationCenter(
    private val context: Context,
) : NotificationCenter {
    override fun show(
        title: String,
        body: String,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val fullScreenIntent =
            PendingIntent.getActivity(
                context,
                FULL_SCREEN_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_CHALLENGE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun dismiss() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_CHALLENGE = "sisyphus_challenge"
        const val NOTIFICATION_ID = 100
        const val FULL_SCREEN_REQUEST_CODE = 200

        fun createChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_CHALLENGE, "Challenge", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
