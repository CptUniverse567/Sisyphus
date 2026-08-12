package org.sisyphus.core.steps

data class StepGuard(
    val maxSingleDelta: Long = DEFAULT_MAX_SINGLE_DELTA,
) {
    companion object {
        const val DEFAULT_MAX_SINGLE_DELTA = 300L
    }
}
