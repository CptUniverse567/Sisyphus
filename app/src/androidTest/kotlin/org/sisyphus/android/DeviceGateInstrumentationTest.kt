package org.sisyphus.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.sisyphus.android.platform.SensorSupport
import org.sisyphus.core.challenge.ChallengeState

@RunWith(AndroidJUnit4::class)
class DeviceGateInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app: SisyphusApp
        get() = context.applicationContext as SisyphusApp
    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun realSensor_updatesProgress() {
        assumeTrue("Device gate requires a real step sensor", SensorSupport.isAvailable(context))
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()
        app.graph.engine.startChallenge()
        assertTrue(app.graph.engine.snapshot().state == ChallengeState.CHALLENGE_ACTIVE)
    }

    @Test
    fun lockAndUnlock_preservesProgress() {
        assumeTrue("Device gate requires a real step sensor", SensorSupport.isAvailable(context))
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()
        app.graph.engine.startChallenge()
        app.graph.engine.onSensorEvent((app.graph.sensor.currentReading() ?: 0L) + 100)
        app.graph.publishState()
        val before = app.graph.engine.snapshot().completedSteps

        device.sleep()
        device.wakeUp()
        assertTrue(app.graph.engine.snapshot().completedSteps >= before)
        assertTrue(app.graph.engine.snapshot().state == ChallengeState.CHALLENGE_ACTIVE)
    }

    @Test
    fun alarmSurvivesProcessRestart() {
        assumeTrue("Device gate requires a real step sensor", SensorSupport.isAvailable(context))
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()
        app.graph.engine.startChallenge()
        app.graph.engine.onSensorEvent((app.graph.sensor.currentReading() ?: 0L) + 250)
        app.graph.publishState()

        val restarted = AppGraph(context)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, restarted.engine.snapshot().state)
        assertEquals(250, restarted.engine.snapshot().completedSteps)
    }
}
