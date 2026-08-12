package org.sisyphus.core.persistence

import org.sisyphus.core.challenge.Challenge
import org.sisyphus.core.challenge.ChallengeState
import org.sisyphus.core.platform.KeyValueStore

class ChallengeRepository(private val store: KeyValueStore) {
    fun save(challenge: Challenge) {
        if (challenge.state == ChallengeState.IDLE) {
            store.remove(KEY_STATE)
            store.remove(KEY_REQUIRED_STEPS)
            store.remove(KEY_COMPLETED_STEPS)
            store.remove(KEY_SENSOR_BASELINE)
            store.remove(KEY_ALARM_FIRE_TIME)
            return
        }
        store.putString(KEY_STATE, challenge.state.name)
        store.putInt(KEY_REQUIRED_STEPS, challenge.requiredSteps)
        store.putInt(KEY_COMPLETED_STEPS, challenge.completedSteps)
        if (challenge.sensorBaseline != null) {
            store.putLong(KEY_SENSOR_BASELINE, challenge.sensorBaseline)
        } else {
            store.remove(KEY_SENSOR_BASELINE)
        }
        if (challenge.alarmFireTimeMillis != null) {
            store.putLong(KEY_ALARM_FIRE_TIME, challenge.alarmFireTimeMillis)
        } else {
            store.remove(KEY_ALARM_FIRE_TIME)
        }
    }

    fun load(): Challenge? {
        val stateName = store.getString(KEY_STATE) ?: return null
        val state = runCatching { ChallengeState.valueOf(stateName) }.getOrNull() ?: return null
        val required = store.getInt(KEY_REQUIRED_STEPS) ?: return null
        if (required < 1 || required > 10000) return null
        val completed = (store.getInt(KEY_COMPLETED_STEPS) ?: 0).coerceIn(0, required)
        val baseline = store.getLong(KEY_SENSOR_BASELINE)
        val fireTime = store.getLong(KEY_ALARM_FIRE_TIME)
        return Challenge(
            state = state,
            requiredSteps = required,
            completedSteps = completed,
            sensorBaseline = baseline,
            alarmFireTimeMillis = fireTime,
        )
    }

    companion object {
        const val KEY_STATE = "challenge.state"
        const val KEY_REQUIRED_STEPS = "challenge.requiredSteps"
        const val KEY_COMPLETED_STEPS = "challenge.completedSteps"
        const val KEY_SENSOR_BASELINE = "challenge.sensorBaseline"
        const val KEY_ALARM_FIRE_TIME = "challenge.alarmFireTime"
    }
}
