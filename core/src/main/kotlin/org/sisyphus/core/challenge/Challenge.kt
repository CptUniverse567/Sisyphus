package org.sisyphus.core.challenge

data class Challenge(
    val state: ChallengeState = ChallengeState.IDLE,
    val requiredSteps: Int,
    val completedSteps: Int = 0,
    val sensorBaseline: Long? = null,
    val alarmFireTimeMillis: Long? = null,
) {
    init {
        require(completedSteps >= 0) { "completedSteps cannot be negative: $completedSteps" }
        require(completedSteps <= requiredSteps) {
            "completedSteps cannot exceed requiredSteps: $completedSteps > $requiredSteps"
        }
    }

    val remainingSteps: Int get() = requiredSteps - completedSteps
    val isComplete: Boolean get() = completedSteps >= requiredSteps
}
