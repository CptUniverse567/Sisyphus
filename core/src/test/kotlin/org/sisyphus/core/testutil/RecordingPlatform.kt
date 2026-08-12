package org.sisyphus.core.testutil

import org.sisyphus.core.platform.AlarmPlayer
import org.sisyphus.core.platform.NotificationCenter
import org.sisyphus.core.sound.ResolvedSound

class RecordingAlarmPlayer : AlarmPlayer {
    val starts = mutableListOf<ResolvedSound>()
    val stops = mutableListOf<Unit>()
    var isPlaying = false

    override fun start(sound: ResolvedSound) {
        starts.add(sound)
        isPlaying = true
    }

    override fun stop() {
        stops.add(Unit)
        isPlaying = false
    }
}

class RecordingNotificationCenter : NotificationCenter {
    val shown = mutableListOf<Pair<String, String>>()
    var dismissCount = 0

    override fun show(
        title: String,
        body: String,
    ) {
        shown.add(title to body)
    }

    override fun dismiss() {
        dismissCount++
    }
}
