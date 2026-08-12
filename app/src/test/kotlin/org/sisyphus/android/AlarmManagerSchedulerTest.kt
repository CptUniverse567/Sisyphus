package org.sisyphus.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sisyphus.android.platform.AlarmManagerScheduler
import org.sisyphus.android.platform.SharedPrefsKeyValueStore

@RunWith(RobolectricTestRunner::class)
class AlarmManagerSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store =
        SharedPrefsKeyValueStore(
            context.getSharedPreferences("sisyphus_test", Context.MODE_PRIVATE),
        )
    private val scheduler = AlarmManagerScheduler(context, store)

    @Test
    fun `scheduling persists the next fire time`() {
        scheduler.schedule(fireAtMillis = 1_000_000L, tag = "t")
        assertEquals(1_000_000L, scheduler.pendingFireAtMillis())
    }

    @Test
    fun `replacement keeps a single pending time`() {
        scheduler.schedule(1_000L, "t")
        scheduler.schedule(2_000L, "t")
        assertEquals(2_000L, scheduler.pendingFireAtMillis())
    }

    @Test
    fun `cancelling clears the pending time`() {
        scheduler.schedule(1_000L, "t")
        scheduler.cancel("t")
        assertNull(scheduler.pendingFireAtMillis())
    }

    @Test
    fun `cancelling when nothing is pending is safe`() {
        scheduler.cancel("t")
        assertNull(scheduler.pendingFireAtMillis())
    }
}
