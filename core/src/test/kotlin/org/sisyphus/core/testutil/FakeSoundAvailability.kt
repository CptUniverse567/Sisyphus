package org.sisyphus.core.testutil

import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.sound.SoundAvailability

class FakeSoundAvailability(private var playable: Boolean = true) : SoundAvailability {
    val queried = mutableListOf<SoundSelection>()

    override fun isPlayable(selection: SoundSelection): Boolean {
        queried.add(selection)
        return playable
    }

    fun setPlayable(value: Boolean) {
        playable = value
    }
}
