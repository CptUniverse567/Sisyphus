package org.sisyphus.core.challenge

data class StepRequirement(val requiredSteps: Int) {
    init {
        require(requiredSteps in MIN_REQUIRED_STEPS..MAX_REQUIRED_STEPS) {
            "requiredSteps must be within $MIN_REQUIRED_STEPS..$MAX_REQUIRED_STEPS but was $requiredSteps"
        }
    }

    val preset: StepPreset get() = StepPreset.fromSteps(requiredSteps)

    companion object {
        const val MIN_REQUIRED_STEPS = 1
        const val MAX_REQUIRED_STEPS = 10000
        val DEFAULT = StepRequirement(500)
    }
}
