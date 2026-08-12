package org.sisyphus.android.platform

import android.content.Context
import android.net.Uri
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.sound.SoundAvailability

class AndroidSoundAvailability(
    private val context: Context,
) : SoundAvailability {
    override fun isPlayable(selection: SoundSelection): Boolean {
        val uriString =
            when (selection) {
                is SoundSelection.SystemRingtone -> selection.uri
                is SoundSelection.CustomFile -> selection.uri
                SoundSelection.Bundled -> return true
            }
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { } != null
        }.getOrDefault(false)
    }
}
