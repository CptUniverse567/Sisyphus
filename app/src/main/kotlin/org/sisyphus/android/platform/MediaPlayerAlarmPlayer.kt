package org.sisyphus.android.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import org.sisyphus.android.R
import org.sisyphus.core.platform.AlarmPlayer
import org.sisyphus.core.sound.ResolvedSound

class MediaPlayerAlarmPlayer(
    private val context: Context,
) : AlarmPlayer {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun start(sound: ResolvedSound) {
        stop()
        val created = prepare(sound) ?: prepare(ResolvedSound.Bundled)
        if (created == null) {
            Log.e(TAG, "alarm sound could not be created for $sound; playing nothing is not acceptable")
            return
        }
        created.isLooping = true
        created.setVolume(1f, 1f)
        created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        requestFocus()
        created.start()
        player = created
        Log.d(TAG, "alarm sound playing: $sound")
    }

    override fun stop() {
        val focus = focusRequest
        focusRequest = null
        if (focus != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { audioManager.abandonAudioFocusRequest(focus) }
        }
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        Log.d(TAG, "alarm sound stopped")
    }

    private fun prepare(sound: ResolvedSound): MediaPlayer? =
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(audioAttributes)
            when (sound) {
                is ResolvedSound.Bundled ->
                    context.resources.openRawResourceFd(R.raw.sisyphus_default)?.use { afd ->
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    } ?: throw IllegalStateException("bundled alarm sound is missing")
                is ResolvedSound.Uri -> mp.setDataSource(context, Uri.parse(sound.uri))
            }
            mp.prepare()
            mp
        }.onFailure {
            Log.w(TAG, "could not prepare alarm sound $sound: ${it.message}")
        }.getOrNull()

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { /* alarms keep ringing */ }
                .build()
        focusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    companion object {
        private const val TAG = "SisyphusAudio"
    }
}
