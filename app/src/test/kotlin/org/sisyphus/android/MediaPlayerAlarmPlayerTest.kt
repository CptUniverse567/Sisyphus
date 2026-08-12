package org.sisyphus.android

import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import org.sisyphus.android.platform.MediaPlayerAlarmPlayer
import org.sisyphus.core.sound.ResolvedSound
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class MediaPlayerAlarmPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player = MediaPlayerAlarmPlayer(context)
    private val createdPlayers = mutableListOf<ShadowMediaPlayer>()

    @Before
    fun setUp() {
        ShadowMediaPlayer.resetStaticState()
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo() }
        ShadowMediaPlayer.setCreateListener { _, shadow -> createdPlayers.add(shadow) }
    }

    @After
    fun tearDown() {
        player.stop()
        ShadowMediaPlayer.resetStaticState()
    }

    @Test
    fun `bundled sound starts playing at full volume`() {
        player.start(ResolvedSound.Bundled)
        val mp = lastMediaPlayer()
        assertTrue(mp.isReallyPlaying)
        assertEquals(1.0f, mp.leftVolume, 0.001f)
        assertEquals(1.0f, mp.rightVolume, 0.001f)
    }

    @Test
    fun `alarm audio attributes use USAGE_ALARM`() {
        player.start(ResolvedSound.Bundled)
        val attrs = lastMediaPlayer().audioAttributes
        assertEquals(AudioAttributes.USAGE_ALARM, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attrs.contentType)
    }

    @Test
    fun `invalid uri falls back to the bundled sound`() {
        val uri = Uri.parse("content://missing/does-not-exist.ogg")
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, uri), IOException("not found"))
        player.start(ResolvedSound.Uri(uri.toString()))
        assertTrue("fallback must create a second player", createdPlayers.size == 2)
        assertTrue(lastMediaPlayer().isReallyPlaying)
    }

    @Test
    fun `stop releases playback and a later start works`() {
        player.start(ResolvedSound.Bundled)
        player.stop()
        val beforeCount = createdPlayers.size
        player.start(ResolvedSound.Bundled)
        assertEquals(beforeCount + 1, createdPlayers.size)
        assertTrue(lastMediaPlayer().isReallyPlaying)
    }

    private fun lastMediaPlayer(): ShadowMediaPlayer {
        assertTrue("no MediaPlayer was created", createdPlayers.isNotEmpty())
        return createdPlayers.last()
    }
}
