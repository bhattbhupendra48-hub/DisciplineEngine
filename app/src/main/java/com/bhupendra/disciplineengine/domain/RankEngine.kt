package com.bhupendra.disciplineengine.domain

// Represents the 8 progression ranks from Bronze to Conqueror
enum class RankTier(val displayName: String, val minRp: Long) {
    BRONZE("Bronze", 0L),
    SILVER("Silver", 500L),
    GOLD("Gold", 1200L),
    PLATINUM("Platinum", 2500L),
    DIAMOND("Diamond", 4000L),
    CROWN("Crown", 5500L),
    ACE("Ace", 7000L),
    CONQUEROR("Conqueror", 9000L)
}

// Defines all blueprint-locked RP reward and penalty sources
enum class RpSource(val label: String, val rpDelta: Int) {
    PUSHUP_ALARM("Morning Pushup Alarm", 25),
    P1_BLOCK("P1 Deep Study Block", 40),
    PW_BLOCK("PW Lecture + DPP Block", 30),
    ZERO_TEMPTATION("Zero Temptation Window", 15),
    PENALTY("Missed Block / Bypass", -35),
    BOSS_PENALTY("Boss Penalty Miss", -50)
}

// Holds computed rank progress values for the UI banner
data class RankProgress(
    val currentTier: RankTier,
    val nextTier: RankTier?,
    val effectiveRp: Long,
    val progressFraction: Float,
    val rpToNextRank: Long
)

object RankEngine {

    // Computes the current rank tier and progress fraction from raw RP
    fun evaluateRank(rawTotalRp: Long): RankProgress {
        val effectiveRp = rawTotalRp.coerceAtLeast(0L)
        val tiers = RankTier.entries
        val current = tiers.lastOrNull { effectiveRp >= it.minRp } ?: RankTier.BRONZE
        val next = tiers.getOrNull(current.ordinal + 1)

        if (next == null) {
            return RankProgress(
                currentTier = current,
                nextTier = null,
                effectiveRp = effectiveRp,
                progressFraction = 1f,
                rpToNextRank = 0L
            )
        }

        val span = (next.minRp - current.minRp).coerceAtLeast(1L)
        val earnedInTier = (effectiveRp - current.minRp).coerceIn(0L, span)
        return RankProgress(
            currentTier = current,
            nextTier = next,
            effectiveRp = effectiveRp,
            progressFraction = earnedInTier.toFloat() / span.toFloat(),
            rpToNextRank = (next.minRp - effectiveRp).coerceAtLeast(0L)
        )
    }

    // Returns all lobby titles unlocked by reaching streak milestones
    fun unlockedTitlesForStreak(bestStreakDays: Int): List<String> {
        val titles = mutableListOf("Rookie Aspirant")
        if (bestStreakDays >= 7) titles.add("5 AM Conqueror")
        if (bestStreakDays >= 14) titles.add("Reel Slayer")
        if (bestStreakDays >= 30) titles.add("Backlog Terminator")
        return titles
    }
}
