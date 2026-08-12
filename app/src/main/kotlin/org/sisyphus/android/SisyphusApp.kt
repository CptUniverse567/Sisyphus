package org.sisyphus.android

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sisyphus.android.platform.AlarmManagerScheduler
import org.sisyphus.android.platform.AndroidSoundAvailability
import org.sisyphus.android.platform.AppNotificationCenter
import org.sisyphus.android.platform.MediaPlayerAlarmPlayer
import org.sisyphus.android.platform.SensorManagerStepSensor
import org.sisyphus.android.platform.SharedPrefsKeyValueStore
import org.sisyphus.android.platform.SystemClock
import org.sisyphus.core.engine.ChallengeViewState
import org.sisyphus.core.engine.SisyphusEngine
import org.sisyphus.core.persistence.ChallengeRepository
import org.sisyphus.core.persistence.SettingsRepository
import org.sisyphus.core.sound.SoundResolver
import org.sisyphus.core.ui.ChallengeUi
import org.sisyphus.core.ui.UiAction

class SisyphusApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        AppNotificationCenter.createChannels(this)
    }
}

class AppGraph(context: Context) {
    val store =
        SharedPrefsKeyValueStore(
            context.getSharedPreferences(SharedPrefsKeyValueStore.PREFS_NAME, Context.MODE_PRIVATE),
        )
    val clock = SystemClock()
    val sensor = SensorManagerStepSensor(context)
    val scheduler = AlarmManagerScheduler(context, store)
    val player = MediaPlayerAlarmPlayer(context)
    val notifications = AppNotificationCenter(context)
    val soundAvailability = AndroidSoundAvailability(context)

    val challengeRepository = ChallengeRepository(store)
    val settingsRepository = SettingsRepository(store)

    var engine: SisyphusEngine = createEngine()
        private set

    private val _engineState = MutableStateFlow(engine.snapshot())
    val engineState: StateFlow<ChallengeViewState> = _engineState.asStateFlow()

    fun availableActions(): Set<UiAction> = ChallengeUi(engine).availableActions()

    fun publishState() {
        _engineState.value = engine.snapshot()
    }

    fun reset() {
        store.clear()
        engine = createEngine()
        publishState()
    }

    private fun createEngine(): SisyphusEngine =
        SisyphusEngine(
            clock = clock,
            sensor = sensor,
            scheduler = scheduler,
            alarmPlayer = player,
            notifications = notifications,
            challengeRepository = challengeRepository,
            settingsRepository = settingsRepository,
            soundResolver = SoundResolver(soundAvailability),
        )
}
