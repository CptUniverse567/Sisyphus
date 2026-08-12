package org.sisyphus.core.steps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepAccountingTest {
    @Test
    fun `baseline is established on the first reading`() {
        val acct = StepAccountant()
        assertEquals(0, acct.onSensorReading(0))
        assertEquals(0L, acct.baseline)
    }

    @Test
    fun `normal cumulative sensor progression counts deltas`() {
        val acct = StepAccountant()
        acct.startBaseline(100)
        assertEquals(0, acct.onSensorReading(100))
        assertEquals(10, acct.onSensorReading(110))
        assertEquals(30, acct.onSensorReading(140))
        assertEquals(60, acct.onSensorReading(200))
        assertEquals(90, acct.onSensorReading(290))
    }

    @Test
    fun `duplicate sensor events add nothing`() {
        val acct = StepAccountant()
        acct.startBaseline(100)
        assertEquals(10, acct.onSensorReading(110))
        assertEquals(0, acct.onSensorReading(110))
        assertEquals(0, acct.onSensorReading(110))
        assertEquals(0, acct.onSensorReading(100))
    }

    @Test
    fun `sensor reset to zero re-baselines without crediting`() {
        val acct = StepAccountant()
        acct.startBaseline(5000)
        assertEquals(200, acct.onSensorReading(5200))
        assertEquals(0, acct.onSensorReading(0))
        assertEquals(0L, acct.baseline)
        assertEquals(10, acct.onSensorReading(10))
    }

    @Test
    fun `reading lower than baseline re-baselines without negative credit`() {
        val acct = StepAccountant()
        acct.startBaseline(100)
        assertEquals(0, acct.onSensorReading(50))
        assertEquals(50L, acct.baseline)
    }

    @Test
    fun `device reboot during a challenge never grants steps`() {
        val acct = StepAccountant()
        acct.startBaseline(5000)
        assertEquals(100, acct.onSensorReading(5100))
        assertEquals(0, acct.onSensorReading(120))
        assertEquals(120L, acct.baseline)
        assertEquals(30, acct.onSensorReading(150))
    }

    @Test
    fun `large unexpected jump is absorbed without credit`() {
        val acct = StepAccountant(StepGuard(maxSingleDelta = 300))
        acct.startBaseline(1000)
        assertEquals(0, acct.onSensorReading(1_000_000))
        assertEquals(1_000_000L, acct.baseline)
        assertEquals(50, acct.onSensorReading(1_000_050))
    }

    @Test
    fun `jump exactly at the guard limit is credited`() {
        val acct = StepAccountant(StepGuard(maxSingleDelta = 300))
        acct.startBaseline(1000)
        assertEquals(300, acct.onSensorReading(1300))
    }

    @Test
    fun `jump above the guard limit is not credited`() {
        val acct = StepAccountant(StepGuard(maxSingleDelta = 300))
        acct.startBaseline(1000)
        assertEquals(0, acct.onSensorReading(1301))
    }

    @Test
    fun `long absence cannot grant thousands of steps in one reading`() {
        val acct = StepAccountant()
        acct.startBaseline(100)
        assertEquals(0, acct.onSensorReading(100 + 10_000L))
    }

    @Test
    fun `sensor unavailable resets the accountant`() {
        val acct = StepAccountant()
        acct.startBaseline(100)
        acct.reset()
        assertNull(acct.baseline)
        assertEquals(0, acct.onSensorReading(500))
        assertEquals(500L, acct.baseline)
    }

    @Test
    fun `restored baseline resumes delta accounting from persisted progress`() {
        val acct = StepAccountant()
        acct.restoreBaseline(5000)
        assertEquals(183, acct.onSensorReading(5183))
    }

    @Test
    fun `previously persisted progress combined with a new reading`() {
        val acct = StepAccountant()
        acct.restoreBaseline(5000)
        assertEquals(200, acct.onSensorReading(5200))
        assertEquals(0, acct.onSensorReading(5200))
        assertEquals(40, acct.onSensorReading(5240))
    }

    @Test
    fun `custom guard values are honoured`() {
        val acct = StepAccountant(StepGuard(maxSingleDelta = 10))
        acct.startBaseline(0)
        assertEquals(10, acct.onSensorReading(10))
        assertEquals(0, acct.onSensorReading(21))
        assertEquals(5, acct.onSensorReading(26))
    }
}
