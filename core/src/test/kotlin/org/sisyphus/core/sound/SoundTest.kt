package org.sisyphus.core.sound

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.testutil.EngineHarness
import org.sisyphus.core.testutil.FakeSoundAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundResolverTest {
    @Test
    fun `bundled default sound is always chosen`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = false))
        assertEquals(ResolvedSound.Bundled, resolver.resolve(SoundSelection.Bundled))
    }

    @Test
    fun `playable system ringtone is resolved to its uri`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = true))
        val selection = SoundSelection.SystemRingtone("content://media/system/1")
        assertEquals(ResolvedSound.Uri("content://media/system/1"), resolver.resolve(selection))
    }

    @Test
    fun `playable custom file is resolved to its uri`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = true))
        val selection = SoundSelection.CustomFile("content://com.example/sound.ogg")
        assertEquals(ResolvedSound.Uri("content://com.example/sound.ogg"), resolver.resolve(selection))
    }

    @Test
    fun `missing custom file falls back to bundled rather than silence`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = false))
        val selection = SoundSelection.CustomFile("content://deleted/missing.ogg")
        assertEquals(ResolvedSound.Bundled, resolver.resolve(selection))
    }

    @Test
    fun `invalid system ringtone falls back to bundled`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = false))
        val selection = SoundSelection.SystemRingtone("content://gone/ringtone")
        assertEquals(ResolvedSound.Bundled, resolver.resolve(selection))
    }

    @Test
    fun `unplayable audio file falls back to bundled`() {
        val resolver = SoundResolver(FakeSoundAvailability(playable = false))
        assertEquals(ResolvedSound.Bundled, resolver.resolve(SoundSelection.CustomFile("content://broken/file.mp3")))
    }

    @Test
    fun `availability is queried only for uri based selections`() {
        val availability = FakeSoundAvailability(playable = true)
        val resolver = SoundResolver(availability)
        resolver.resolve(SoundSelection.Bundled)
        resolver.resolve(SoundSelection.SystemRingtone("uri:1"))
        resolver.resolve(SoundSelection.CustomFile("uri:2"))
        assertEquals(2, availability.queried.size)
    }
}

class AlarmSoundBehaviourTest {
    @Test
    fun `alarm sound starts with the resolved selection`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.SystemRingtone("content://media/system/ring"))
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()

        assertTrue(harness.player.isPlaying)
        assertEquals(1, harness.player.starts.size)
        assertEquals(ResolvedSound.Uri("content://media/system/ring"), harness.player.starts.single())
    }

    @Test
    fun `custom sound that becomes unavailable falls back to bundled on fire`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.CustomFile("content://example/deleted.ogg"))
        harness.soundAvailability.setPlayable(false)

        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(ResolvedSound.Bundled, harness.player.starts.single())
    }

    @Test
    fun `alarm sound stops immediately at completion`() {
        val harness = EngineHarness()
        harness.armAndStart(requiredSteps = 500)
        assertTrue(harness.player.isPlaying)

        harness.sensor.setReading(250)
        harness.engine.onSensorEvent(250)
        harness.sensor.setReading(500)
        harness.engine.onSensorEvent(500)
        assertTrue(!harness.player.isPlaying)
        assertEquals(ChallengeState.COMPLETED, harness.engine.snapshot().state)
    }

    @Test
    fun `sound selection persists across process death`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.CustomFile("content://persisted/tone.ogg"))

        val rebuilt = harness.rebuild()
        assertEquals(
            SoundSelection.CustomFile("content://persisted/tone.ogg"),
            rebuilt.currentSettings().soundSelection,
        )
    }

    @Test
    fun `reboot after selecting custom audio keeps the selection and falls back if missing`() {
        val harness = EngineHarness()
        harness.engine.selectSound(SoundSelection.CustomFile("content://persisted/tone.ogg"))

        val afterReboot = harness.rebuild()
        assertEquals(
            SoundSelection.CustomFile("content://persisted/tone.ogg"),
            afterReboot.currentSettings().soundSelection,
        )

        harness.soundAvailability.setPlayable(false)
        afterReboot.configureAlarm(500, 6, 0)
        afterReboot.onAlarmFired()
        assertEquals(ResolvedSound.Bundled, harness.player.starts.single())
    }

    @Test
    fun `sound resumes after service restart during the alarm`() {
        val harness = EngineHarness()
        harness.engine.configureAlarm(500, 6, 0)
        harness.engine.onAlarmFired()
        assertEquals(1, harness.player.starts.size)

        val restored = harness.rebuild()
        restored.resumeChallengeSound()
        assertEquals(2, harness.player.starts.size)
        assertTrue(harness.player.isPlaying)
    }
}
