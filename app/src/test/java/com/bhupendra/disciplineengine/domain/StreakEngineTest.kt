package com.bhupendra.disciplineengine.domain

import com.bhupendra.disciplineengine.data.StreakEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakEngineTest {

    // Verifies missing 1 day triggers S2 Cracked Emblem and -35 RP, and missing 2nd shatters to 0
    @Test
    fun oneMissCracksAndTwoMissesShatters() {
        val base = StreakEntity(
            currentStreak = 10,
            bestStreak = 10,
            totalRp = 600,
            lastEvaluatedEpochDay = 100L
        )
        val day1Miss = StreakEngine.evaluateDayTransition(
            state = base,
            todayEpochDay = 101L,
            yesterdayKeystoneCompleted = false,
            nowMillis = 1_000_000L,
            currentYearMonth = 202611
        )
        assertTrue(day1Miss.updatedState.isCracked)
        assertEquals(StreakStatus.CRACKED_PENALTY_24H.name, day1Miss.updatedState.streakStatus)
        assertEquals(-35, day1Miss.rpDeltaApplied)
        assertEquals(565L, day1Miss.updatedState.totalRp)

        val day2Miss = StreakEngine.evaluateDayTransition(
            state = day1Miss.updatedState,
            todayEpochDay = 102L,
            yesterdayKeystoneCompleted = false,
            nowMillis = 2_000_000L,
            currentYearMonth = 202611
        )
        assertFalse(day2Miss.updatedState.isCracked)
        assertEquals(0, day2Miss.updatedState.currentStreak)
        assertEquals(StreakStatus.SHATTERED_ZERO.name, day2Miss.updatedState.streakStatus)
    }

    // Verifies Boss repair welds a cracked streak only when reps and P1 tasks are satisfied
    @Test
    fun bossRepairWeldsCrackedStreak() {
        val cracked = StreakEntity(
            currentStreak = 8,
            isCracked = true,
            crackedExpireAt = 5_000_000L,
            streakStatus = StreakStatus.CRACKED_PENALTY_24H.name,
            penaltyRepsRequired = 40
        )
        val repaired = StreakEngine.repairCrackedStreak(
            state = cracked,
            completedBossReps = 40,
            todayP1Completed = true,
            nowMillis = 2_000_000L
        )
        assertFalse(repaired.isCracked)
        assertEquals(StreakStatus.ACTIVE_FLAME.name, repaired.streakStatus)
    }

    // Verifies Anti-Fake Sick Rule blocks Illness mode activation at or after 10:00 AM
    @Test
    fun illnessModeAllowedOnlyBefore10Am() {
        val base = StreakEntity(reelsTimeBankMinutes = 25)
        val rejected = StreakEngine.activateIllnessTravelMode(base, 100L, currentHour24 = 10)
        assertTrue(rejected is IllnessModeResult.RejectedAfterCutoff)

        val accepted = StreakEngine.activateIllnessTravelMode(base, 100L, currentHour24 = 9)
        assertTrue(accepted is IllnessModeResult.Activated)
        val activeState = (accepted as IllnessModeResult.Activated).updatedState
        assertEquals(0, activeState.reelsTimeBankMinutes)
        assertTrue(StreakEngine.isIllnessHardLockActive(activeState, 100L))
    }

    // Verifies verified pushups pay Dopamine Debt first before earning Reels Bank minutes
    @Test
    fun dopamineDebtClearsBeforeReelsMinutesEarned() {
        val afterBypass = StreakEngine.applyEmergencyBypass(
            StreakEntity(totalRp = 200, reelsTimeBankMinutes = 15, dopamineDebtPushups = 0)
        )
        assertEquals(165L, afterBypass.totalRp)
        assertEquals(0, afterBypass.reelsTimeBankMinutes)
        assertEquals(30, afterBypass.dopamineDebtPushups)

        val after50Pushups = StreakEngine.recordVerifiedPushupsForBank(afterBypass, 50)
        assertEquals(0, after50Pushups.dopamineDebtPushups)
        assertEquals(10, after50Pushups.reelsTimeBankMinutes)
    }
}
