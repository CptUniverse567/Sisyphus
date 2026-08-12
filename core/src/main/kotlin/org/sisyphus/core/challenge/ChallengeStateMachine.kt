package org.sisyphus.core.challenge

class ChallengeStateMachine {
    fun arm(
        current: Challenge,
        steps: Int,
        fireAtMillis: Long,
    ): Challenge {
        requireState(current, ChallengeState.IDLE) { "Cannot arm a challenge in state ${current.state}" }
        val requirement = StepRequirement(steps)
        return current.copy(
            state = ChallengeState.ARMED,
            requiredSteps = requirement.requiredSteps,
            completedSteps = 0,
            sensorBaseline = null,
            alarmFireTimeMillis = fireAtMillis,
        )
    }

    fun cancel(current: Challenge): Challenge {
        requireState(current, ChallengeState.ARMED, ChallengeState.RINGING) {
            "Cannot cancel a challenge in state ${current.state}"
        }
        return current.copy(
            state = ChallengeState.IDLE,
            completedSteps = 0,
            sensorBaseline = null,
            alarmFireTimeMillis = null,
        )
    }

    fun alarmFired(current: Challenge): Challenge {
        requireState(current, ChallengeState.ARMED) { "Alarm cannot fire from state ${current.state}" }
        return current.copy(state = ChallengeState.RINGING)
    }

    fun startChallenge(
        current: Challenge,
        baseline: Long,
    ): Challenge {
        requireState(current, ChallengeState.RINGING) {
            "Challenge can only start from RINGING, was ${current.state}"
        }
        return current.copy(
            state = ChallengeState.CHALLENGE_ACTIVE,
            completedSteps = 0,
            sensorBaseline = baseline,
        )
    }

    fun progress(
        current: Challenge,
        additionalSteps: Int,
    ): Challenge {
        requireState(current, ChallengeState.CHALLENGE_ACTIVE) {
            "Cannot progress a challenge in state ${current.state}"
        }
        require(additionalSteps >= 0) { "additionalSteps cannot be negative: $additionalSteps" }
        val completed = (current.completedSteps + additionalSteps).coerceAtMost(current.requiredSteps)
        val next = current.copy(completedSteps = completed)
        return if (completed >= current.requiredSteps) next.copy(state = ChallengeState.COMPLETED) else next
    }

    fun complete(current: Challenge): Challenge {
        requireState(current, ChallengeState.CHALLENGE_ACTIVE, ChallengeState.COMPLETED) {
            "Cannot complete a challenge in state ${current.state}"
        }
        return current.copy(state = ChallengeState.COMPLETED, completedSteps = current.requiredSteps)
    }

    fun acknowledge(current: Challenge): Challenge {
        requireState(current, ChallengeState.COMPLETED) { "Cannot acknowledge in state ${current.state}" }
        return current.copy(
            state = ChallengeState.IDLE,
            completedSteps = 0,
            sensorBaseline = null,
            alarmFireTimeMillis = null,
        )
    }

    private fun requireState(
        current: Challenge,
        vararg allowed: ChallengeState,
        message: () -> String,
    ) {
        if (current.state !in allowed) throw IllegalStateException(message())
    }
}
