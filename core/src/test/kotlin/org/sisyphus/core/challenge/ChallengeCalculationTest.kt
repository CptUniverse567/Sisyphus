package org.sisyphus.core.challenge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChallengeCalculationTest {
    private val sm = ChallengeStateMachine()

    private fun activeChallenge(required: Int): Challenge =
        sm.startChallenge(
            sm.alarmFired(sm.arm(Challenge(requiredSteps = required), required, fireAtMillis = 1L)),
            baseline = 0L,
        )

    @Test
    fun `five hundred required means five hundred remaining initially`() {
        val c = activeChallenge(500)
        assertEquals(ChallengeState.CHALLENGE_ACTIVE, c.state)
        assertEquals(500, c.requiredSteps)
        assertEquals(0, c.completedSteps)
        assertEquals(500, c.remainingSteps)
    }

    @Test
    fun `five hundred required becomes four hundred ninety nine after one valid step`() {
        val c = sm.progress(activeChallenge(500), 1)
        assertEquals(1, c.completedSteps)
        assertEquals(499, c.remainingSteps)
    }

    @Test
    fun `five hundred required reaches zero when the requirement is met`() {
        val c = sm.progress(activeChallenge(500), 500)
        assertEquals(500, c.completedSteps)
        assertEquals(0, c.remainingSteps)
        assertEquals(ChallengeState.COMPLETED, c.state)
    }

    @Test
    fun `progress never becomes negative`() {
        val c = sm.progress(activeChallenge(500), 600)
        assertEquals(500, c.completedSteps)
        assertEquals(0, c.remainingSteps)
        assertEquals(ChallengeState.COMPLETED, c.state)
    }

    @Test
    fun `excess sensor steps cannot produce invalid state`() {
        val c = sm.progress(activeChallenge(500), 5000)
        assertEquals(500, c.completedSteps)
        assertEquals(0, c.remainingSteps)
        assertEquals(ChallengeState.COMPLETED, c.state)
    }

    @Test
    fun `different presets map to their documented requirements`() {
        assertEquals(500, StepPreset.LIGHT.steps)
        assertEquals(1000, StepPreset.MEDIUM.steps)
        assertEquals(2000, StepPreset.HEAVY.steps)
        assertEquals(StepPreset.LIGHT, StepPreset.fromSteps(500))
        assertEquals(StepPreset.MEDIUM, StepPreset.fromSteps(1000))
        assertEquals(StepPreset.HEAVY, StepPreset.fromSteps(2000))
    }

    @Test
    fun `non preset values are custom`() {
        assertEquals(StepPreset.CUSTOM, StepPreset.fromSteps(750))
        assertEquals(StepPreset.CUSTOM, StepPreset.fromSteps(1))
    }

    @Test
    fun `custom step requirements are supported`() {
        val c = activeChallenge(777)
        assertEquals(777, c.requiredSteps)
        assertEquals(777, c.remainingSteps)
    }

    @Test
    fun `minimum allowed value is accepted and completable`() {
        val requirement = StepRequirement(StepRequirement.MIN_REQUIRED_STEPS)
        assertEquals(1, requirement.requiredSteps)
        assertEquals(ChallengeState.COMPLETED, sm.progress(activeChallenge(1), 1).state)
    }

    @Test
    fun `maximum allowed value is accepted`() {
        assertEquals(10000, StepRequirement(StepRequirement.MAX_REQUIRED_STEPS).requiredSteps)
    }

    @Test
    fun `values below the minimum are rejected`() {
        assertFailsWith<IllegalArgumentException> { StepRequirement(0) }
        assertFailsWith<IllegalArgumentException> { StepRequirement(-1) }
        assertFailsWith<IllegalArgumentException> { StepRequirement(-500) }
    }

    @Test
    fun `values above the maximum are rejected`() {
        assertFailsWith<IllegalArgumentException> { StepRequirement(10001) }
        assertFailsWith<IllegalArgumentException> { StepRequirement(Int.MAX_VALUE) }
    }

    @Test
    fun `challenge completion is idempotent`() {
        val active = activeChallenge(500)
        val once = sm.complete(active)
        val twice = sm.complete(once)
        assertEquals(once, twice)
        assertEquals(ChallengeState.COMPLETED, twice.state)
        assertEquals(0, twice.remainingSteps)
    }

    @Test
    fun `reaching the requirement through progress also idempotently reports completed`() {
        val reached = sm.progress(activeChallenge(500), 500)
        val again = sm.complete(reached)
        assertEquals(reached, again)
    }
}
