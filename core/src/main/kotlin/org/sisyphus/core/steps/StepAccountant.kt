package org.sisyphus.core.steps

class StepAccountant(private val guard: StepGuard = StepGuard()) {
    var baseline: Long? = null
        private set

    fun startBaseline(currentReading: Long) {
        baseline = currentReading
    }

    fun restoreBaseline(value: Long?) {
        baseline = value
    }

    fun onSensorReading(currentReading: Long): Int {
        val base = baseline
        if (base == null) {
            baseline = currentReading
            return 0
        }
        val delta = currentReading - base
        if (delta <= 0L) {
            baseline = currentReading
            return 0
        }
        if (delta > guard.maxSingleDelta) {
            baseline = currentReading
            return 0
        }
        baseline = currentReading
        return delta.toInt()
    }

    fun reset() {
        baseline = null
    }
}
