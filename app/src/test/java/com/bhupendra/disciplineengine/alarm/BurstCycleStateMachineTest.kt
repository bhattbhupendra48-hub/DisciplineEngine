package com.bhupendra.disciplineengine.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstCycleStateMachineTest {

    // Verifies 3x3 burst transitions across 3 min ring + 2 min pause up to 15 min timeout
    @Test
    fun runsThreeBurstCyclesBeforeAbandonTimeout() {
        val sm = BurstCycleStateMachine(ringDurationMs = 180_000L, pauseDurationMs = 120_000L, maxCycles = 3)
        sm.start(0L)

        assertEquals(BurstPhase.PAUSED, sm.tick(180_000L).phase)
        assertEquals(BurstPhase.RINGING, sm.tick(300_000L).phase)
        assertEquals(2, sm.snapshot(300_000L).cycleNumber)

        assertEquals(BurstPhase.PAUSED, sm.tick(480_000L).phase)
        assertEquals(BurstPhase.RINGING, sm.tick(600_000L).phase)
        assertEquals(3, sm.snapshot(600_000L).cycleNumber)

        val timeout = sm.tick(780_000L)
        assertEquals(BurstPhase.ABANDONED_TIMEOUT, timeout.phase)
    }

    // Verifies physical motion/touch registers without blocking dismissal
    @Test
    fun interactionAndDismissWork() {
        val sm = BurstCycleStateMachine()
        sm.start(1000L)
        sm.registerInteraction()
        val dismissed = sm.markDismissed(5000L)
        assertTrue(dismissed.motionOrTouchDetected)
        assertEquals(BurstPhase.COMPLETED_DISMISSED, dismissed.phase)
    }
}
