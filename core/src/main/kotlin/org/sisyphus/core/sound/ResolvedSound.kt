package org.sisyphus.core.sound

sealed class ResolvedSound {
    data object Bundled : ResolvedSound()

    data class Uri(val uri: String) : ResolvedSound()
}
