package org.sisyphus.core.engine

import org.sisyphus.core.challenge.Challenge
import org.sisyphus.core.challenge.ChallengeState

data class ChallengeViewState(
    val state: ChallengeState,
    val requiredSteps: Int,
    val completedSteps: Int,
    val remainingSteps: Int,
    val isActive: Boolean,
    val alarmFireTimeMillis: Long?,
) {
    val isChallengeRunning: Boolean get() = isActive

    companion object {
        fun of(challenge: Challenge): ChallengeViewState =
            ChallengeViewState(
                state = challenge.state,
                requiredSteps = challenge.requiredSteps,
                completedSteps = challenge.completedSteps,
                remainingSteps = challenge.remainingSteps,
                isActive =
                    challenge.state == ChallengeState.CHALLENGE_ACTIVE ||
                        challenge.state == ChallengeState.RINGING,
                alarmFireTimeMillis = challenge.alarmFireTimeMillis,
            )
    }
}
