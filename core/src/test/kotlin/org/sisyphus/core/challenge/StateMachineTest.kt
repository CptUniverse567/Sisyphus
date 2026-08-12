package org.sisyphus.core.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StateMachineTest {
    private val sm = ChallengeStateMachine()
    private val idle = Challenge(requiredSteps = 500)

    private val armed get() = sm.arm(idle, 500, fireAtMillis = 1000L)
    private val ringing get() = sm.alarmFired(armed)
    private val active get() = sm.startChallenge(ringing, baseline = 0L)
    private val completed get() = sm.progress(active, 500)

    @Test
    fun `idle arms to armed`() {
        val next = sm.arm(idle, 500, fireAtMillis = 1000L)
        assertEquals(ChallengeState.ARMED, next.state)
        assertEquals(1000L, next.alarmFireTimeMillis)
    }

    @Test
    fun `armed fires to ringing`() {
        assertEquals(ChallengeState.RINGING, ringing.state)
    }

    @Test
    fun `ringing starts the challenge active`() {
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, active.state)
        assertEquals(0L, active.sensorBaseline)
    }

    @Test
    fun `challenge active completes when the requirement is reached`() {
        val next = sm.progress(active, 500)
        assertEquals(ChallengeState.COMPLETED, next.state)
    }

    @Test
    fun `completed acknowledges back to idle`() {
        val next = sm.acknowledge(completed)
        assertEquals(ChallengeState.IDLE, next.state)
        assertEquals(0, next.completedSteps)
        assertEquals(500, next.requiredSteps)
    }

    @Test
    fun `armed cancels to idle`() {
        val next = sm.cancel(armed)
        assertEquals(ChallengeState.IDLE, next.state)
        assertEquals(null, next.alarmFireTimeMillis)
    }

    @Test
    fun `ringing can be cancelled only through the explicit emergency cancel`() {
        assertEquals(ChallengeState.IDLE, sm.cancel(ringing).state)
    }

    @Test
    fun `cannot complete an inactive challenge`() {
        assertFailsWith<IllegalStateException> { sm.complete(idle) }
        assertFailsWith<IllegalStateException> { sm.complete(armed) }
        assertFailsWith<IllegalStateException> { sm.complete(ringing) }
    }

    @Test
    fun `cannot arm a challenge that is already armed`() {
        assertFailsWith<IllegalStateException> { sm.arm(armed, 500, fireAtMillis = 2000L) }
    }

    @Test
    fun `cannot edit a ringing or active challenge`() {
        assertFailsWith<IllegalStateException> { sm.arm(ringing, 500, fireAtMillis = 2000L) }
        assertFailsWith<IllegalStateException> { sm.arm(active, 500, fireAtMillis = 2000L) }
    }

    @Test
    fun `cannot snooze or pause an active challenge`() {
        assertFailsWith<IllegalStateException> { sm.acknowledge(active) }
        assertFailsWith<IllegalStateException> { sm.cancel(active) }
    }

    @Test
    fun `cannot restart a completed challenge accidentally`() {
        assertFailsWith<IllegalStateException> { sm.arm(completed, 500, fireAtMillis = 2000L) }
        assertFailsWith<IllegalStateException> { sm.alarmFired(completed) }
    }

    @Test
    fun `cannot create a duplicate active challenge`() {
        assertFailsWith<IllegalStateException> { sm.arm(armed, 500, fireAtMillis = 2000L) }
        assertFailsWith<IllegalStateException> { sm.arm(ringing, 500, fireAtMillis = 2000L) }
        assertFailsWith<IllegalStateException> { sm.arm(active, 500, fireAtMillis = 2000L) }
        assertFailsWith<IllegalStateException> { sm.arm(completed, 500, fireAtMillis = 2000L) }
    }

    @Test
    fun `cannot progress a challenge after completion`() {
        assertFailsWith<IllegalStateException> { sm.progress(completed, 10) }
    }

    @Test
    fun `cannot progress an idle, armed or ringing challenge`() {
        assertFailsWith<IllegalStateException> { sm.progress(idle, 10) }
        assertFailsWith<IllegalStateException> { sm.progress(armed, 10) }
        assertFailsWith<IllegalStateException> { sm.progress(ringing, 10) }
    }

    @Test
    fun `negative step deltas are rejected`() {
        assertFailsWith<IllegalArgumentException> { sm.progress(active, -1) }
    }

    @Test
    fun `alarm cannot fire from inactive states`() {
        assertFailsWith<IllegalStateException> { sm.alarmFired(idle) }
        assertFailsWith<IllegalStateException> { sm.alarmFired(active) }
        assertFailsWith<IllegalStateException> { sm.alarmFired(completed) }
    }

    @Test
    fun `challenge cannot start unless the alarm is ringing`() {
        assertFailsWith<IllegalStateException> { sm.startChallenge(idle, baseline = 0L) }
        assertFailsWith<IllegalStateException> { sm.startChallenge(armed, baseline = 0L) }
        assertFailsWith<IllegalStateException> { sm.startChallenge(completed, baseline = 0L) }
    }

    @Test
    fun `cannot acknowledge a challenge that is not completed`() {
        assertFailsWith<IllegalStateException> { sm.acknowledge(idle) }
        assertFailsWith<IllegalStateException> { sm.acknowledge(armed) }
        assertFailsWith<IllegalStateException> { sm.acknowledge(ringing) }
        assertFailsWith<IllegalStateException> { sm.acknowledge(active) }
    }

    @Test
    fun `lifecycle restore preserves the active challenge`() {
        val restored = active.copy()
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, restored.state)
        assertEquals(0, restored.completedSteps)
        assertEquals(500, restored.remainingSteps)
    }
}
