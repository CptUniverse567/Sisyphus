package org.sisyphus.core.persistence

import org.sisyphus.core.alarm.Alarm
import org.sisyphus.core.alarm.RepeatMode
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.testutil.InMemoryKeyValueStore
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmRepositoryTest {
    private val store = InMemoryKeyValueStore()
    private val repo = AlarmRepository(store)

    @Test
    fun `empty store loads an empty list`() {
        assertTrue(repo.loadAll().isEmpty())
    }

    @Test
    fun `alarms round-trip through the store with all fields`() {
        val alarms =
            listOf(
                Alarm(
                    id = "alarm-1",
                    hour = 6,
                    minute = 30,
                    repeatMode = RepeatMode.WEEKDAYS,
                    enabled = true,
                    requiredSteps = 700,
                    soundSelection = SoundSelection.CustomFile("content://x/s.ogg"),
                ),
                Alarm(
                    id = "alarm-2",
                    hour = 9,
                    minute = 0,
                    repeatMode = RepeatMode.CUSTOM,
                    customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    enabled = false,
                    requiredSteps = 1200,
                    soundSelection = SoundSelection.SystemRingtone("content://r"),
                ),
                Alarm(
                    id = "alarm-3",
                    hour = 22,
                    minute = 15,
                    repeatMode = RepeatMode.ONCE,
                    onceDate = LocalDate.of(2026, 3, 15),
                    enabled = true,
                    requiredSteps = 300,
                ),
            )

        repo.saveAll(alarms)
        val loaded = repo.loadAll()

        assertEquals(alarms, loaded)
    }

    @Test
    fun `removing an alarm clears its persisted keys`() {
        repo.saveAll(listOf(Alarm(id = "alarm-1", hour = 6, minute = 0)))
        repo.saveAll(emptyList())
        assertTrue(repo.loadAll().isEmpty())
    }

    @Test
    fun `corrupted alarm entry is skipped`() {
        store.putString("alarms.index", "alarm-1,alarm-2")
        store.putInt("alarm.alarm-1.hour", 6)
        store.putInt("alarm.alarm-1.minute", 0)
        store.putString("alarm.alarm-1.repeat", "DAILY")
        store.putInt("alarm.alarm-1.steps", 500)
        // alarm-2 is missing fields -> skipped
        val loaded = repo.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("alarm-1", loaded[0].id)
    }
}
