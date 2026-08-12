package org.sisyphus.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sisyphus.android.platform.AndroidSoundAvailability
import org.sisyphus.core.settings.SoundSelection

@RunWith(RobolectricTestRunner::class)
class AndroidSoundAvailabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val availability = AndroidSoundAvailability(context)

    @Test
    fun `bundled is always playable`() {
        assertTrue(availability.isPlayable(SoundSelection.Bundled))
    }

    @Test
    fun `missing custom file is not playable`() {
        assertFalse(availability.isPlayable(SoundSelection.CustomFile("content://missing/nope.ogg")))
    }

    @Test
    fun `malformed uri is not playable`() {
        assertFalse(availability.isPlayable(SoundSelection.SystemRingtone("not a uri")))
    }

    @Test
    fun `empty uri is not playable`() {
        assertFalse(availability.isPlayable(SoundSelection.CustomFile("")))
    }
}
