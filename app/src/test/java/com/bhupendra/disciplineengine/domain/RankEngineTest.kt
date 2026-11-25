package com.bhupendra.disciplineengine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankEngineTest {

    // Verifies Bronze to Conqueror rank boundaries match master_blueprint.json
    @Test
    fun rankThresholdsMatchBlueprint() {
        assertEquals(RankTier.BRONZE, RankEngine.evaluateRank(0).currentTier)
        assertEquals(RankTier.SILVER, RankEngine.evaluateRank(500).currentTier)
        assertEquals(RankTier.GOLD, RankEngine.evaluateRank(1200).currentTier)
        assertEquals(RankTier.PLATINUM, RankEngine.evaluateRank(2500).currentTier)
        assertEquals(RankTier.DIAMOND, RankEngine.evaluateRank(4000).currentTier)
        assertEquals(RankTier.CROWN, RankEngine.evaluateRank(5500).currentTier)
        assertEquals(RankTier.ACE, RankEngine.evaluateRank(7000).currentTier)
        val conqueror = RankEngine.evaluateRank(9500)
        assertEquals(RankTier.CONQUEROR, conqueror.currentTier)
        assertNull(conqueror.nextTier)
        assertEquals(1f, conqueror.progressFraction, 0.001f)
    }

    // Verifies lobby titles unlock progressively at 7, 14, and 30 day streaks
    @Test
    fun titlesUnlockAtStreakMilestones() {
        assertEquals(listOf("Rookie Aspirant"), RankEngine.unlockedTitlesForStreak(6))
        assertEquals(2, RankEngine.unlockedTitlesForStreak(7).size)
        assertEquals(3, RankEngine.unlockedTitlesForStreak(14).size)
        assertEquals(4, RankEngine.unlockedTitlesForStreak(30).size)
    }
}
