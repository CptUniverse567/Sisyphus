package org.sisyphus.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sisyphus.android.platform.SharedPrefsKeyValueStore

@RunWith(RobolectricTestRunner::class)
class SharedPrefsKeyValueStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("sisyphus_test", Context.MODE_PRIVATE)
    private val store = SharedPrefsKeyValueStore(prefs)

    @Test
    fun `string values round trip`() {
        assertNull(store.getString("k"))
        store.putString("k", "v")
        assertEquals("v", store.getString("k"))
    }

    @Test
    fun `long values round trip`() {
        assertNull(store.getLong("k"))
        store.putLong("k", 42L)
        assertEquals(42L, store.getLong("k"))
    }

    @Test
    fun `int values round trip`() {
        assertNull(store.getInt("k"))
        store.putInt("k", 183)
        assertEquals(183, store.getInt("k"))
    }

    @Test
    fun `boolean values round trip`() {
        assertNull(store.getBoolean("k"))
        store.putBoolean("k", true)
        assertEquals(true, store.getBoolean("k"))
    }

    @Test
    fun `remove deletes the key`() {
        store.putInt("k", 1)
        store.remove("k")
        assertNull(store.getInt("k"))
    }

    @Test
    fun `clear empties the store`() {
        store.putString("a", "1")
        store.putString("b", "2")
        store.clear()
        assertNull(store.getString("a"))
        assertNull(store.getString("b"))
    }
}
