package org.sisyphus.android.platform

import org.sisyphus.core.platform.Clock
import java.time.ZoneId

class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
