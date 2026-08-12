package org.sisyphus.core.ui

import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.engine.SisyphusEngine

sealed class UiAction {
    data object ConfigureAlarm : UiAction()

    data object CancelAlarm : UiAction()

    data object StartChallenge : UiAction()

    data object AcknowledgeCompletion : UiAction()
}

class ChallengeUi(private val engine: SisyphusEngine) {
    fun availableActions(): Set<UiAction> =
        when (engine.snapshot().state) {
            ChallengeState.IDLE -> setOf(UiAction.ConfigureAlarm)
            ChallengeState.ARMED -> setOf(UiAction.CancelAlarm)
            ChallengeState.RINGING -> setOf(UiAction.StartChallenge)
            ChallengeState.CHALLENGE_ACTIVE -> emptySet()
            ChallengeState.COMPLETED -> setOf(UiAction.AcknowledgeCompletion)
        }
}
