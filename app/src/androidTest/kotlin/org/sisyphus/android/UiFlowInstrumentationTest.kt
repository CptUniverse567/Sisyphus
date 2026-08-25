package org.sisyphus.android

import android.text.format.DateFormat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.sisyphus.android.platform.SensorSupport
import org.sisyphus.android.ui.MainActivity
import org.sisyphus.core.challenge.ChallengeState

class UiFlowInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: SisyphusApp
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SisyphusApp
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun requireStepSensor() {
        assumeTrue(
            "Requires a real step sensor",
            SensorSupport.isAvailable(InstrumentationRegistry.getInstrumentation().targetContext),
        )
    }

    private fun waitForTag(
        tag: String,
        timeoutMillis: Long = 15_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Before
    fun cleanStart() {
        app.graph.reset()
        app.graph.sensor.unregister()
    }

    @Test
    fun setupFlow_usesNativeTimePicker_noTextFields() {
        composeRule.onNodeWithTag("addAlarm").performClick()
        waitForTag("alarmEditorScreen")

        composeRule.onNodeWithTag("stepsField").performTextClearance()
        composeRule.onNodeWithTag("stepsField").performTextInput("500")

        composeRule.onNodeWithTag("hourField").assertDoesNotExist()
        composeRule.onNodeWithTag("minuteField").assertDoesNotExist()

        composeRule.onNodeWithTag("timeField").performClick()
        device.wait(Until.hasObject(By.text("OK")), 3_000)
        device.pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saveAlarm").performClick()
        waitForTag("alarmListScreen")
        assertEquals(1, app.graph.engine.alarms().size)
        assertEquals(500, app.graph.engine.alarms()[0].requiredSteps)
        assertEquals(6, app.graph.engine.alarms()[0].hour)
        assertEquals(0, app.graph.engine.alarms()[0].minute)
    }

    @Test
    fun setupFlow_acceptsAChosenTime() {
        val is24Hour = DateFormat.is24HourFormat(InstrumentationRegistry.getInstrumentation().targetContext)
        val inputHour = if (is24Hour) "23" else "11"
        composeRule.onNodeWithTag("addAlarm").performClick()
        waitForTag("alarmEditorScreen")
        composeRule.onNodeWithTag("timeField").performClick()
        device.wait(Until.hasObject(By.text("OK")), 3_000)
        setTimeInPicker(inputHour, "59", amPm = "PM")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("timeFieldLabel", useUnmergedTree = true).assertTextEquals("11:59 PM")

        composeRule.onNodeWithTag("saveAlarm").performClick()
        waitForTag("alarmListScreen")
        assertEquals(23, app.graph.engine.alarms()[0].hour)
        assertEquals(59, app.graph.engine.alarms()[0].minute)
    }

    @Test
    fun alarmFlow_updatesProgress_completes() {
        requireStepSensor()
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()
        app.graph.publishState()

        waitForTag("alarmScreen")
        composeRule.onNodeWithTag("startChallenge").performClick()

        waitForTag("challengeScreen")

        val base = app.graph.sensor.currentReading() ?: 0L
        app.graph.engine.onSensorEvent(base + 100)
        app.graph.publishState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remainingSteps").assertTextEquals("400")

        app.graph.engine.onSensorEvent(base + 300)
        app.graph.publishState()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remainingSteps").assertTextEquals("200")

        app.graph.engine.onSensorEvent(base + 500)
        app.graph.publishState()
        composeRule.waitForIdle()
        waitForTag("completedScreen")
    }

    @Test
    fun challengeScreen_exposesNoStopOrSnooze() {
        requireStepSensor()
        startActiveChallenge()
        waitForTag("challengeScreen")
        composeRule.onNodeWithTag("stopButton").assertDoesNotExist()
        composeRule.onNodeWithTag("snoozeButton").assertDoesNotExist()
    }

    @Test
    fun backButton_doesNotDismissTheChallenge() {
        requireStepSensor()
        startActiveChallenge(progress = 100)
        Espresso.pressBack()
        composeRule.waitForIdle()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, app.graph.engine.snapshot().state)
        assertEquals(400, app.graph.engine.snapshot().remainingSteps)
    }

    @Test
    fun activityRecreation_doesNotResetProgress() {
        requireStepSensor()
        startActiveChallenge(progress = 100)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForTag("challengeScreen")
        composeRule.onNodeWithTag("remainingSteps").assertTextEquals("400")
    }

    private fun setTimeInPicker(
        hour: String,
        minute: String,
        amPm: String = "AM",
    ) {
        var attempts = 0
        while (attempts < 5) {
            if (device.wait(Until.hasObject(By.res("android:id/input_hour")), 1_000)) break
            if (device.wait(Until.hasObject(By.res("android:id/toggle_mode")), 1_000)) {
                device.findObject(By.res("android:id/toggle_mode")).click()
            } else {
                break
            }
            attempts++
        }
        device.findObject(By.res("android:id/input_hour")).text = hour
        device.findObject(By.res("android:id/input_minute")).text = minute
        val amPmSpinner =
            device.wait(
                Until.findObject(By.res("android:id/am_pm_spinner")),
                1_000,
            )
        if (amPmSpinner != null) {
            amPmSpinner.click()
            device.wait(Until.hasObject(By.text(amPm)), 1_000)
            device.findObject(By.text(amPm)).click()
        }
        device.findObject(By.res("android:id/button1")).click()
    }

    private fun startActiveChallenge(progress: Int = 0) {
        app.graph.engine.configureAlarm(500, 6, 0)
        app.graph.engine.onAlarmFired()
        app.graph.publishState()
        composeRule.onNodeWithTag("startChallenge").performClick()
        composeRule.waitForIdle()
        if (progress > 0) {
            val base = app.graph.sensor.currentReading() ?: 0L
            app.graph.engine.onSensorEvent(base + progress)
            app.graph.publishState()
            composeRule.waitForIdle()
        }
    }
}
