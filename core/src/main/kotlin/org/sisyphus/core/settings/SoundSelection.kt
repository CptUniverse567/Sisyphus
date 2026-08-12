package org.sisyphus.core.settings

sealed class SoundSelection {
    data object Bundled : SoundSelection()

    data class SystemRingtone(val uri: String) : SoundSelection()

    data class CustomFile(val uri: String) : SoundSelection()
}
