package com.bhupendra.disciplineengine.domain

import com.bhupendra.disciplineengine.data.StreakEntity
import java.util.concurrent.TimeUnit

// Defines all four S2 streak visual and penalty states
enum class StreakStatus {
    ACTIVE_FLAME,
    CRACKED_PENALTY_24H,
    FROZEN_SICK_TRAVEL,
    SHATTERED_ZERO
}

// Result of attempting to enable Illness / Travel Mode before 10 AM
sealed interface IllnessModeResult {
    data class Activated(val updatedState: StreakEntity) : IllnessModeResult
    data object RejectedAfterCutoff : IllnessModeResult
}

// Holds Ghost Rival comparison between today and 7 days ago
data class GhostRivalDelta(
    val todayPercent: Float,
    val lastWeekPercent: Float,
    val deltaPercent: Float
)

// Result of evaluating a day rollover in the streak engine
data class DayTransitionResult(
    val updatedState: StreakEntity,
    val rpDeltaApplied: Int,
    val reason: String,
    val shouldApplyProgressiveOverload: Boolean
)

object StreakEngine {

    private const val GRACE_WINDOW_MS = 24L * 60L * 60L * 1000L
    private const val ILLNESS_CUTOFF_HOUR = 10
    private const val BYPASS_DEBT_PUSHUPS = 30
    private const val MAX_EMERGENCY_SHIELDS = 2

    // Checks whether Illness / Travel hard lock is currently active today
    fun isIllnessHardLockActive(state: StreakEntity, todayEpochDay: Long): Boolean {
        return state.illnessModeActiveEpochDay == todayEpochDay &&
            state.streakStatus == StreakStatus.FROZEN_SICK_TRAVEL.name
    }

    // Enables Illness / Travel Mode only when local hour is strictly before 10:00 AM
    fun activateIllnessTravelMode(
        state: StreakEntity,
        todayEpochDay: Long,
        currentHour24: Int
    ): IllnessModeResult {
        if (currentHour24 >= ILLNESS_CUTOFF_HOUR) {
            return IllnessModeResult.RejectedAfterCutoff
        }
        val updated = state.copy(
            streakStatus = StreakStatus.FROZEN_SICK_TRAVEL.name,
            illnessModeActiveEpochDay = todayEpochDay,
            reelsTimeBankMinutes = 0
        )
        return IllnessModeResult.Activated(updated)
    }

    // Evaluates yesterday's outcome on day change and updates S2/S4/S5 state
    fun evaluateDayTransition(
        state: StreakEntity,
        todayEpochDay: Long,
        yesterdayKeystoneCompleted: Boolean,
        nowMillis: Long,
        currentYearMonth: Int
    ): DayTransitionResult {
        val resetMonthTokensState = if (state.lastTokenResetYearMonth != currentYearMonth) {
            state.copy(
                monthlyDarkRoomBypassTokensLeft = 2,
                lastTokenResetYearMonth = currentYearMonth
            )
        } else {
            state
        }

        val lastEvaluated = resetMonthTokensState.lastEvaluatedEpochDay
        if (lastEvaluated == null || lastEvaluated >= todayEpochDay) {
            return DayTransitionResult(
                updatedState = resetMonthTokensState.copy(lastEvaluatedEpochDay = todayEpochDay),
                rpDeltaApplied = 0,
                reason = "Same day",
                shouldApplyProgressiveOverload = false
            )
        }

        val daysElapsed = todayEpochDay - lastEvaluated
        val wasFrozenYesterday =
            resetMonthTokensState.illnessModeActiveEpochDay == lastEvaluated &&
                resetMonthTokensState.streakStatus == StreakStatus.FROZEN_SICK_TRAVEL.name

        if (wasFrozenYesterday && daysElapsed == 1L) {
            return DayTransitionResult(
                updatedState = resetMonthTokensState.copy(
                    streakStatus = StreakStatus.ACTIVE_FLAME.name,
                    illnessModeActiveEpochDay = null,
                    lastEvaluatedEpochDay = todayEpochDay
                ),
                rpDeltaApplied = 0,
                reason = "Illness/Travel day safely preserved",
                shouldApplyProgressiveOverload = false
            )
        }

        if (daysElapsed == 1L && yesterdayKeystoneCompleted) {
            val nextStreak = resetMonthTokensState.currentStreak + 1
            val nextBest = maxOf(resetMonthTokensState.bestStreak, nextStreak)
            val earnedShield = if (nextStreak > 0 && nextStreak % 14 == 0) {
                minOf(MAX_EMERGENCY_SHIELDS, resetMonthTokensState.earnedEmergencyShields + 1)
            } else {
                resetMonthTokensState.earnedEmergencyShields
            }
            val overload = nextStreak > 0 && nextStreak % 7 == 0
            val updated = resetMonthTokensState.copy(
                currentStreak = nextStreak,
                bestStreak = nextBest,
                isCracked = false,
                crackedExpireAt = 0L,
                streakStatus = StreakStatus.ACTIVE_FLAME.name,
                earnedEmergencyShields = earnedShield,
                illnessModeActiveEpochDay = null,
                lastEvaluatedEpochDay = todayEpochDay,
                statCON = (resetMonthTokensState.statCON + 1.5f).coerceAtMost(100f)
            )
            return DayTransitionResult(
                updatedState = updated,
                rpDeltaApplied = 0,
                reason = "Streak advanced to $nextStreak days",
                shouldApplyProgressiveOverload = overload
            )
        }

        val alreadyCrackedOrMultiDayGap =
            resetMonthTokensState.isCracked ||
                resetMonthTokensState.streakStatus == StreakStatus.CRACKED_PENALTY_24H.name ||
                daysElapsed >= 2L

        if (alreadyCrackedOrMultiDayGap) {
            val updated = resetMonthTokensState.copy(
                currentStreak = 0,
                isCracked = false,
                crackedExpireAt = 0L,
                streakStatus = StreakStatus.SHATTERED_ZERO.name,
                lastMissDate = nowMillis,
                illnessModeActiveEpochDay = null,
                lastEvaluatedEpochDay = todayEpochDay,
                totalRp = (resetMonthTokensState.totalRp + RpSource.PENALTY.rpDelta).coerceAtLeast(0L),
                statWIL = (resetMonthTokensState.statWIL - 4f).coerceAtLeast(5f),
                statCON = (resetMonthTokensState.statCON - 5f).coerceAtLeast(5f)
            )
            return DayTransitionResult(
                updatedState = updated,
                rpDeltaApplied = RpSource.PENALTY.rpDelta,
                reason = "Streak shattered after 2 consecutive missed days",
                shouldApplyProgressiveOverload = false
            )
        }

        val crackedState = resetMonthTokensState.copy(
            isCracked = true,
            crackedExpireAt = nowMillis + GRACE_WINDOW_MS,
            streakStatus = StreakStatus.CRACKED_PENALTY_24H.name,
            penaltyRepsRequired = 40,
            lastMissDate = nowMillis,
            illnessModeActiveEpochDay = null,
            lastEvaluatedEpochDay = todayEpochDay,
            totalRp = (resetMonthTokensState.totalRp + RpSource.PENALTY.rpDelta).coerceAtLeast(0L),
            statWIL = (resetMonthTokensState.statWIL - 2f).coerceAtLeast(5f)
        )
        return DayTransitionResult(
            updatedState = crackedState,
            rpDeltaApplied = RpSource.PENALTY.rpDelta,
            reason = "Missed 1 day: S2 Cracked Emblem active for 24h",
            shouldApplyProgressiveOverload = false
        )
    }

    // Welds a cracked S2 streak back to active once boss pushups or squats complete
    fun repairCrackedStreak(
        state: StreakEntity,
        completedBossReps: Int,
        todayP1Completed: Boolean,
        nowMillis: Long,
        isSquatMode: Boolean = false
    ): StreakEntity {
        val required = if (isSquatMode) 60 else state.penaltyRepsRequired
        val withinGraceWindow = state.crackedExpireAt == 0L || nowMillis <= state.crackedExpireAt
        if (!state.isCracked || !withinGraceWindow || completedBossReps < required || !todayP1Completed) {
            return state
        }
        return state.copy(
            isCracked = false,
            crackedExpireAt = 0L,
            streakStatus = StreakStatus.ACTIVE_FLAME.name,
            statWIL = (state.statWIL + 3f).coerceAtMost(100f)
        )
    }

    // Spends one stored emergency shield to immediately weld a cracked streak
    fun consumeEmergencyShield(state: StreakEntity): StreakEntity {
        if (!state.isCracked || state.earnedEmergencyShields <= 0) return state
        return state.copy(
            isCracked = false,
            crackedExpireAt = 0L,
            streakStatus = StreakStatus.ACTIVE_FLAME.name,
            earnedEmergencyShields = state.earnedEmergencyShields - 1
        )
    }

    // Records an emergency bypass: applies -35 RP decay and adds +30 pushup Dopamine Debt
    fun applyEmergencyBypass(state: StreakEntity): StreakEntity {
        return state.copy(
            totalRp = (state.totalRp + RpSource.PENALTY.rpDelta).coerceAtLeast(0L),
            reelsTimeBankMinutes = 0,
            dopamineDebtPushups = state.dopamineDebtPushups + BYPASS_DEBT_PUSHUPS,
            statWIL = (state.statWIL - 2.5f).coerceAtLeast(5f)
        )
    }

    // Pays off Dopamine Debt pushups first, then converts surplus reps into Reels minutes
    fun recordVerifiedPushupsForBank(state: StreakEntity, reps: Int): StreakEntity {
        if (reps <= 0) return state
        val debtCleared = minOf(state.dopamineDebtPushups, reps)
        val remainingDebt = state.dopamineDebtPushups - debtCleared
        val surplusReps = reps - debtCleared
        val earnedMinutes = (surplusReps / 20) * 10
        return state.copy(
            dopamineDebtPushups = remainingDebt,
            reelsTimeBankMinutes = state.reelsTimeBankMinutes + earnedMinutes,
            statSTR = (state.statSTR + (reps * 0.1f)).coerceAtMost(100f)
        )
    }

    // Computes Ghost Rival percentage delta between today and 7 days ago
    fun computeGhostDelta(todayCompletionPct: Float, weekAgoCompletionPct: Float): GhostRivalDelta {
        return GhostRivalDelta(
            todayPercent = todayCompletionPct,
            lastWeekPercent = weekAgoCompletionPct,
            deltaPercent = todayCompletionPct - weekAgoCompletionPct
        )
    }

    // Formats remaining hours in the 24h S2 Cracked Emblem grace window
    fun remainingGraceHours(state: StreakEntity, nowMillis: Long): Long {
        if (!state.isCracked || state.crackedExpireAt <= nowMillis) return 0L
        return TimeUnit.MILLISECONDS.toHours(state.crackedExpireAt - nowMillis).coerceAtLeast(1L)
    }
}
