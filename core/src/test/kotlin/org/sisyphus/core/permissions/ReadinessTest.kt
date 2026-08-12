package org.sisyphus.core.permissions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessTest {
    private val checker = ReadinessChecker()

    @Test
    fun `everything granted is ready`() {
        val report = readyReport()
        assertTrue(report.isReady)
        assertEquals(emptyList(), report.missing)
        assertEquals(emptyList(), report.missingRequirements)
    }

    @Test
    fun `denied notifications explain exactly what is missing`() {
        val report =
            readyReport().copy(
                notificationsAllowed = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.NOTIFICATIONS), report.missingRequirements)
        assertTrue(report.missing.single().contains("Notifications"))
    }

    @Test
    fun `device without a step sensor explains what is missing`() {
        val report =
            readyReport().copy(
                sensorAvailable = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.STEP_SENSOR), report.missingRequirements)
        assertTrue(report.missing.single().contains("step sensor"))
    }

    @Test
    fun `disabled notifications are reported`() {
        val report =
            readyReport().copy(
                notificationsAllowed = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.NOTIFICATIONS), report.missingRequirements)
    }

    @Test
    fun `revoked exact alarm access is reported`() {
        val report =
            readyReport().copy(
                exactAlarmsAllowed = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.EXACT_ALARMS), report.missingRequirements)
        assertTrue(report.missing.single().contains("Exact alarm"))
    }

    @Test
    fun `revoked full screen alarm access is reported`() {
        val report =
            readyReport().copy(
                fullScreenAlarmAllowed = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.FULL_SCREEN_INTENT), report.missingRequirements)
        assertTrue(report.missing.single().contains("Full-screen"))
    }

    @Test
    fun `missing alarm sound is reported`() {
        val report =
            readyReport().copy(
                hasAlarmSound = false,
            )
        assertFalse(report.isReady)
        assertEquals(listOf(ReadinessRequirement.ALARM_SOUND), report.missingRequirements)
        assertTrue(report.missing.single().contains("sound"))
    }

    @Test
    fun `multiple missing requirements are all listed`() {
        val report =
            readyReport().copy(
                notificationsAllowed = false,
                sensorAvailable = false,
                hasAlarmSound = false,
                exactAlarmsAllowed = false,
                fullScreenAlarmAllowed = false,
            )
        assertFalse(report.isReady)
        assertEquals(5, report.missing.size)
        assertEquals(5, report.missingRequirements.size)
        report.missing.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `granting permission later flips readiness`() {
        val before =
            readyReport().copy(
                notificationsAllowed = false,
            )
        assertFalse(before.isReady)

        val after = readyReport()
        assertTrue(after.isReady)
    }

    @Test
    fun `revoking permission after setup is detected`() {
        assertTrue(readyReport().isReady)

        val revoked =
            readyReport().copy(
                exactAlarmsAllowed = false,
            )
        assertFalse(revoked.isReady)
    }

    private fun readyReport(): ReadinessReport =
        checker.check(
            notificationsAllowed = true,
            sensorAvailable = true,
            hasAlarmSound = true,
            exactAlarmsAllowed = true,
            fullScreenAlarmAllowed = true,
        )
}
