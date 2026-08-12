package org.sisyphus.android.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.sisyphus.android.SisyphusApp
import org.sisyphus.android.service.SisyphusService
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.engine.ChallengeViewState
import org.sisyphus.core.settings.SoundSelection
import org.sisyphus.core.ui.UiAction

class ChallengeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as SisyphusApp).graph
    private val engine get() = graph.engine
    private val context get() = getApplication<Application>()

    val state: StateFlow<ChallengeViewState> = graph.engineState

    val availableActions: Set<UiAction> get() = graph.availableActions()

    var challengeError: String? = null
        private set

    fun refresh() = graph.publishState()

    fun configureAlarm(
        steps: Int,
        hour: Int,
        minute: Int,
    ) {
        challengeError = null
        engine.configureAlarm(steps, hour, minute)
        refresh()
    }

    fun cancelAlarm() {
        challengeError = null
        engine.cancelAlarm()
        stopTracking()
        refresh()
    }

    fun startChallenge() {
        challengeError = null
        try {
            engine.startChallenge()
            notifyService(SisyphusService.ACTION_START_CHALLENGE)
            refresh()
        } catch (e: Exception) {
            challengeError = e.message ?: "Could not start the challenge."
        }
    }

    fun acknowledge() {
        challengeError = null
        engine.acknowledgeCompletion()
        stopTracking()
        refresh()
    }

    fun selectSound(selection: SoundSelection) {
        engine.selectSound(selection)
        refresh()
    }

    val isArmed: Boolean get() = engine.snapshot().state == ChallengeState.ARMED

    val isChallengeActive: Boolean get() = engine.snapshot().state == ChallengeState.CHALLENGE_ACTIVE

    val settings get() = engine.currentSettings()

    fun resumeTracking() {
        if (isChallengeActive) notifyService(SisyphusService.ACTION_START_SENSOR)
    }

    fun stopTracking() {
        if (isChallengeActive) notifyService(SisyphusService.ACTION_STOP_SENSOR)
    }

    private fun notifyService(action: String) {
        context.startService(Intent(context, SisyphusService::class.java).setAction(action))
    }
}
