package com.bhupendra.disciplineengine.data

import com.bhupendra.disciplineengine.domain.GhostRivalDelta
import com.bhupendra.disciplineengine.domain.IllnessModeResult
import com.bhupendra.disciplineengine.domain.RankEngine
import com.bhupendra.disciplineengine.domain.RankProgress
import com.bhupendra.disciplineengine.domain.RpSource
import com.bhupendra.disciplineengine.domain.StreakEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

// Snapshot of all Phase 2 dashboard data loaded from Room
data class DashboardSnapshot(
    val streakState: StreakEntity,
    val rankProgress: RankProgress,
    val habits: List<HabitEntity>,
    val recentEvents: List<RpEventEntity>,
    val unlockedTitles: List<String>,
    val ghostRival: GhostRivalDelta,
    val isIllnessHardLockActive: Boolean
)

class DisciplineRepository(private val dao: DisciplineDao) {

    // Ensures default habits and singleton streak row exist, then evaluates day rollover
    suspend fun loadDashboard(): DashboardSnapshot = withContext(Dispatchers.IO) {
        seedDefaultsIfEmpty()
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val yearMonth = today.year * 100 + today.monthValue
        val nowMillis = System.currentTimeMillis()

        var state = dao.getStreak() ?: StreakEntity(lastEvaluatedEpochDay = todayEpoch)
        val habitsBeforeReset = dao.getAllHabits()
        val lastEval = state.lastEvaluatedEpochDay

        if (lastEval != null && lastEval < todayEpoch) {
            val yesterdayKeystoneDone = habitsBeforeReset
                .filter { it.isKeystone }
                .all { it.lastCompletedEpochDay == lastEval }

            val transition = StreakEngine.evaluateDayTransition(
                state = state,
                todayEpochDay = todayEpoch,
                yesterdayKeystoneCompleted = yesterdayKeystoneDone,
                nowMillis = nowMillis,
                currentYearMonth = yearMonth
            )
            state = transition.updatedState
            if (transition.rpDeltaApplied != 0) {
                dao.addRpEvent(
                    RpEventEntity(
                        timestamp = nowMillis,
                        source = RpSource.PENALTY.name,
                        rpDelta = transition.rpDeltaApplied
                    )
                )
            }
            habitsBeforeReset.forEach { habit ->
                val newTarget = if (transition.shouldApplyProgressiveOverload && habit.targetValue > 1) {
                    (habit.targetValue * habit.progressiveOverloadRate).roundToInt()
                } else {
                    habit.targetValue
                }
                dao.addHabit(
                    habit.copy(
                        targetValue = newTarget,
                        currentValue = 0,
                        isCompletedToday = false
                    )
                )
            }
        } else if (lastEval == null) {
            state = state.copy(lastEvaluatedEpochDay = todayEpoch)
        }

        val syncedRp = dao.totalRp().coerceAtLeast(0L)
        state = state.copy(totalRp = syncedRp)
        dao.upsertStreak(state)

        val habits = dao.getAllHabits()
        val completionPct = computeCompletionPercent(habits)
        val ghostLog = dao.getAnalyticsForDay(todayEpoch - 7L)
        val ghostDelta = StreakEngine.computeGhostDelta(
            todayCompletionPct = completionPct,
            weekAgoCompletionPct = ghostLog?.habitsCompletedPercent ?: 0f
        )

        syncTodayAnalytics(todayEpoch, completionPct)

        DashboardSnapshot(
            streakState = state,
            rankProgress = RankEngine.evaluateRank(state.totalRp),
            habits = habits,
            recentEvents = dao.getRecentRpEvents(10),
            unlockedTitles = RankEngine.unlockedTitlesForStreak(state.bestStreak),
            ghostRival = ghostDelta,
            isIllnessHardLockActive = StreakEngine.isIllnessHardLockActive(state, todayEpoch)
        )
    }

    // Completes a habit once per day and awards its exact blueprint RP
    suspend fun completeHabit(habitId: String): DashboardSnapshot = withContext(Dispatchers.IO) {
        val habits = dao.getAllHabits()
        val target = habits.firstOrNull { it.habitId == habitId } ?: return@withContext loadDashboard()
        if (target.isCompletedToday) return@withContext loadDashboard()

        val todayEpoch = LocalDate.now().toEpochDay()
        dao.addHabit(
            target.copy(
                currentValue = target.targetValue,
                isCompletedToday = true,
                lastCompletedEpochDay = todayEpoch
            )
        )

        val source = when (target.verificationSource) {
            "CAMERA_PUSHUP" -> RpSource.PUSHUP_ALARM
            "FOCUS_TIMER" -> RpSource.P1_BLOCK
            "PW_LECTURE_SYNC" -> RpSource.PW_BLOCK
            "ZERO_REELS_BLOCKER" -> RpSource.ZERO_TEMPTATION
            else -> RpSource.P1_BLOCK
        }
        awardRpInternal(source, target.rpReward)
        loadDashboard()
    }

    // Records an emergency bypass penalty (-35 RP and +30 Dopamine Debt pushups)
    suspend fun triggerEmergencyBypass(): DashboardSnapshot = withContext(Dispatchers.IO) {
        val current = dao.getStreak() ?: StreakEntity()
        val updated = StreakEngine.applyEmergencyBypass(current)
        dao.addRpEvent(
            RpEventEntity(
                timestamp = System.currentTimeMillis(),
                source = RpSource.PENALTY.name,
                rpDelta = RpSource.PENALTY.rpDelta
            )
        )
        dao.upsertStreak(updated.copy(totalRp = dao.totalRp().coerceAtLeast(0L)))
        loadDashboard()
    }

    // Records verified pushup reps to clear Dopamine Debt or deposit Reels Time Bank
    suspend fun recordVerifiedPushups(reps: Int): DashboardSnapshot = withContext(Dispatchers.IO) {
        val current = dao.getStreak() ?: StreakEntity()
        val updated = StreakEngine.recordVerifiedPushupsForBank(current, reps)
        dao.upsertStreak(updated)
        loadDashboard()
    }

    // Repairs an S2 Cracked streak once boss pushups/squats and P1 tasks are completed
    suspend fun attemptBossRepair(bossReps: Int, isSquats: Boolean): DashboardSnapshot =
        withContext(Dispatchers.IO) {
            val current = dao.getStreak() ?: StreakEntity()
            val habits = dao.getAllHabits()
            val p1Done = habits.any { it.isKeystone && it.isCompletedToday }
            val repaired = StreakEngine.repairCrackedStreak(
                state = current,
                completedBossReps = bossReps,
                todayP1Completed = p1Done,
                nowMillis = System.currentTimeMillis(),
                isSquatMode = isSquats
            )
            dao.upsertStreak(repaired)
            loadDashboard()
        }

    // Spends one stored Emergency Shield to weld a cracked streak
    suspend fun useEmergencyShield(): DashboardSnapshot = withContext(Dispatchers.IO) {
        val current = dao.getStreak() ?: StreakEntity()
        dao.upsertStreak(StreakEngine.consumeEmergencyShield(current))
        loadDashboard()
    }

    // Activates Illness/Travel mode if local time is before 10:00 AM
    suspend fun tryEnableIllnessMode(): Boolean = withContext(Dispatchers.IO) {
        val current = dao.getStreak() ?: StreakEntity()
        val todayEpoch = LocalDate.now().toEpochDay()
        val hour = LocalTime.now().hour
        when (val result = StreakEngine.activateIllnessTravelMode(current, todayEpoch, hour)) {
            is IllnessModeResult.Activated -> {
                dao.upsertStreak(result.updatedState)
                true
            }
            IllnessModeResult.RejectedAfterCutoff -> false
        }
    }

    // Equips an unlocked RPG lobby title
    suspend fun equipTitle(title: String): DashboardSnapshot = withContext(Dispatchers.IO) {
        val current = dao.getStreak() ?: StreakEntity()
        dao.upsertStreak(current.copy(equippedTitle = title))
        loadDashboard()
    }

    // Logs an RP event and updates the matching RPG radar stat
    private suspend fun awardRpInternal(source: RpSource, rpAmount: Int) {
        dao.addRpEvent(
            RpEventEntity(
                timestamp = System.currentTimeMillis(),
                source = source.name,
                rpDelta = rpAmount
            )
        )
        val current = dao.getStreak() ?: StreakEntity()
        val boosted = when (source) {
            RpSource.PUSHUP_ALARM -> current.copy(
                statSTR = (current.statSTR + 2.0f).coerceAtMost(100f),
                statSPD = (current.statSPD + 1.5f).coerceAtMost(100f)
            )
            RpSource.P1_BLOCK -> current.copy(
                statINT = (current.statINT + 2.5f).coerceAtMost(100f),
                statCON = (current.statCON + 1.0f).coerceAtMost(100f)
            )
            RpSource.PW_BLOCK -> current.copy(
                statINT = (current.statINT + 2.0f).coerceAtMost(100f)
            )
            RpSource.ZERO_TEMPTATION -> current.copy(
                statWIL = (current.statWIL + 2.5f).coerceAtMost(100f)
            )
            else -> current
        }
        dao.upsertStreak(boosted.copy(totalRp = dao.totalRp().coerceAtLeast(0L)))
    }

    // Updates today's analytics row so Ghost Rival comparisons stay accurate
    private suspend fun syncTodayAnalytics(todayEpoch: Long, habitsCompletionPct: Float) {
        val existing = dao.getAnalyticsForDay(todayEpoch) ?: DailyAnalyticsEntity(dateEpochDay = todayEpoch)
        dao.upsertDailyAnalytics(existing.copy(habitsCompletedPercent = habitsCompletionPct))
    }

    // Calculates today's habit completion percentage from 0 to 100
    private fun computeCompletionPercent(habits: List<HabitEntity>): Float {
        if (habits.isEmpty()) return 0f
        val completed = habits.count { it.isCompletedToday }
        return (completed.toFloat() / habits.size.toFloat()) * 100f
    }

    // Seeds the four core blueprint habits and initial RPG state on first launch
    private suspend fun seedDefaultsIfEmpty() {
        if (dao.getStreak() == null) {
            dao.upsertStreak(StreakEntity(lastEvaluatedEpochDay = LocalDate.now().toEpochDay()))
        }
        if (dao.getAllHabits().isEmpty()) {
            dao.insertHabitsIfMissing(
                listOf(
                    HabitEntity(
                        habitId = "habit_pushup_alarm",
                        title = "Morning Pushup Alarm (20 reps)",
                        tier = "AUTO_VERIFIED_GOLD",
                        verificationSource = "CAMERA_PUSHUP",
                        rpReward = RpSource.PUSHUP_ALARM.rpDelta,
                        isKeystone = true,
                        targetValue = 20
                    ),
                    HabitEntity(
                        habitId = "habit_p1_study",
                        title = "P1 Deep Study Block (90 mins)",
                        tier = "AUTO_VERIFIED_GOLD",
                        verificationSource = "FOCUS_TIMER",
                        rpReward = RpSource.P1_BLOCK.rpDelta,
                        isKeystone = true,
                        targetValue = 90
                    ),
                    HabitEntity(
                        habitId = "habit_pw_lecture",
                        title = "PW Lecture + DPP Block",
                        tier = "AUTO_VERIFIED_GOLD",
                        verificationSource = "PW_LECTURE_SYNC",
                        rpReward = RpSource.PW_BLOCK.rpDelta,
                        isKeystone = true,
                        targetValue = 1
                    ),
                    HabitEntity(
                        habitId = "habit_zero_reels",
                        title = "Zero Reels During Study Hours",
                        tier = "AUTO_VERIFIED_GOLD",
                        verificationSource = "ZERO_REELS_BLOCKER",
                        rpReward = RpSource.ZERO_TEMPTATION.rpDelta,
                        isKeystone = false,
                        targetValue = 1
                    )
                )
            )
        }
    }
}
