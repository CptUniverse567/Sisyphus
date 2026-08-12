package org.sisyphus.android.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.sisyphus.core.platform.SensorStatus
import org.sisyphus.core.platform.StepSensor

class SensorManagerStepSensor(context: Context) : StepSensor, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetector: Sensor? =
        if (stepCounter == null) sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null

    @Volatile
    private var lastReading: Long = 0L
    private var detectorAccumulator: Long = 0L
    private var listener: ((Long) -> Unit)? = null
    private var registered = false

    override fun status(): SensorStatus =
        if (stepCounter != null || stepDetector != null) SensorStatus.AVAILABLE else SensorStatus.UNAVAILABLE

    override fun currentReading(): Long? = lastReading

    fun register(onStepRead: (Long) -> Unit) {
        listener = onStepRead
        val sensor = stepCounter ?: stepDetector
        if (sensor == null) {
            Log.w(TAG, "listener NOT registered: no step sensor on this device")
            return
        }
        if (registered) {
            Log.d(TAG, "listener already registered")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
        Log.d(TAG, "listener registered (sensor type=${sensor.type} counter=${stepCounter != null})")
    }

    fun unregister() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        listener = null
        Log.d(TAG, "listener unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> lastReading = event.values[0].toLong()
            Sensor.TYPE_STEP_DETECTOR -> {
                detectorAccumulator += 1
                lastReading = detectorAccumulator
            }
        }
        Log.d(TAG, "raw sensor value = $lastReading")
        listener?.invoke(lastReading)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit

    companion object {
        private const val TAG = "SisyphusSensor"
    }
}
