package org.sisyphus.core.testutil

import org.sisyphus.core.platform.SensorStatus
import org.sisyphus.core.platform.StepSensor

class FakeStepSensor : StepSensor {
    var statusValue: SensorStatus = SensorStatus.AVAILABLE
    var readingValue: Long? = 0L

    override fun status(): SensorStatus = statusValue

    override fun currentReading(): Long? = readingValue

    fun setReading(value: Long) {
        readingValue = value
    }

    fun setUnavailable() {
        statusValue = SensorStatus.UNAVAILABLE
        readingValue = null
    }
}
