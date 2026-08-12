package org.sisyphus.android

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowSensor
import org.sisyphus.android.receiver.AlarmReceiver
import org.sisyphus.android.service.SisyphusService
import org.sisyphus.core.challenge.ChallengeState

@RunWith(RobolectricTestRunner::class)
class AlarmPipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = context.applicationContext as SisyphusApp
    private val createdPlayers = mutableListOf<ShadowMediaPlayer>()

    @Before
    fun setUp() {
        installStepCounter()
        ShadowMediaPlayer.resetStaticState()
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo() }
        ShadowMediaPlayer.setCreateListener { _, shadow -> createdPlayers.add(shadow) }
    }

    @After
    fun tearDown() {
        ShadowMediaPlayer.resetStaticState()
    }

    private fun installStepCounter() {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
    }

    @Test
    fun `alarm receiver forwards to the foreground service`() {
        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java))
        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        val intent = shadowApp.nextStartedService
        assertNotNull(intent)
        assertEquals(SisyphusService::class.java.name, intent!!.component?.className)
        assertEquals(SisyphusService.ACTION_ALARM_FIRED, intent.action)
    }

    @Test
    fun `service alarm fire transitions armed to ringing and starts the sound`() {
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        startService(SisyphusService.ACTION_ALARM_FIRED)

        assertEquals(ChallengeState.RINGING, app.graph.engine.snapshot().state)
        assertTrue(lastMediaPlayer().isReallyPlaying)
    }

    @Test
    fun `duplicate alarm broadcasts do not throw and keep the alarm audible`() {
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        startService(SisyphusService.ACTION_ALARM_FIRED)
        startService(SisyphusService.ACTION_ALARM_FIRED)
        startService(SisyphusService.ACTION_ALARM_FIRED)

        assertEquals(ChallengeState.RINGING, app.graph.engine.snapshot().state)
        assertTrue(lastMediaPlayer().isReallyPlaying)
    }

    @Test
    fun `alarm notification carries a full screen intent`() {
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        assertNotNull(notifications[0].fullScreenIntent)
        assertTrue(notifications[0].category == android.app.Notification.CATEGORY_ALARM)
    }

    @Test
    fun `challenge start through the service keeps remaining steps stable then counts`() {
        app.graph.reset()
        app.graph.engine.configureAlarm(500, 6, 0)
        startService(SisyphusService.ACTION_ALARM_FIRED)
        app.graph.engine.startChallenge()
        assertEquals(500, app.graph.engine.snapshot().remainingSteps)

        app.graph.engine.onSensorEvent(100)
        app.graph.publishState()
        assertEquals(400, app.graph.engine.snapshot().remainingSteps)
    }

    private fun startService(action: String) {
        val service =
            Robolectric
                .buildService(SisyphusService::class.java)
                .create()
                .get()
        service.onStartCommand(Intent(context, SisyphusService::class.java).setAction(action), 0, 1)
    }

    private fun lastMediaPlayer(): ShadowMediaPlayer {
        assertTrue("no MediaPlayer was created", createdPlayers.isNotEmpty())
        return createdPlayers.last()
    }
}
