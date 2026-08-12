package org.sisyphus.core.sound

import org.sisyphus.core.settings.SoundSelection

interface SoundAvailability {
    fun isPlayable(selection: SoundSelection): Boolean
}

class SoundResolver(private val availability: SoundAvailability) {
    fun resolve(selection: SoundSelection): ResolvedSound =
        when (selection) {
            SoundSelection.Bundled -> ResolvedSound.Bundled
            is SoundSelection.SystemRingtone ->
                if (availability.isPlayable(selection)) ResolvedSound.Uri(selection.uri) else ResolvedSound.Bundled
            is SoundSelection.CustomFile ->
                if (availability.isPlayable(selection)) ResolvedSound.Uri(selection.uri) else ResolvedSound.Bundled
        }
}
