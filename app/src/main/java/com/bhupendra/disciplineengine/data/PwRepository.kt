package com.bhupendra.disciplineengine.data

import android.content.Context
import com.bhupendra.disciplineengine.domain.RpSource
import com.bhupendra.disciplineengine.pw.ExamTriagePlan
import com.bhupendra.disciplineengine.pw.OfflineBatchClockEngine
import com.bhupendra.disciplineengine.pw.PwExamTriageEngine
import com.bhupendra.disciplineengine.pw.PwItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

// Holds all live PW notices, exam triage matrix, and missed test vault entries
data class PwDashboardSnapshot(
    val noticesAndTests: List<PwNoticeTestEntity>,
    val chapters: List<PwSyllabusEntity>,
    val triagePlan: ExamTriagePlan,
    val examDaysAhead: Int,
    val dailySelfStudyHours: Float,
    val passiveSyncPendingCount: Int,
    val batchClockAddedLectures: Int,
    val studyTasks: List<PwStudyTaskEntity>
)

class PwRepository(
    private val dao: DisciplineDao,
    private val appContext: Context
) {

    private val prefs = appContext.getSharedPreferences("pw_study_prefs", Context.MODE_PRIVATE)

    // Loads chapters, ticks offline batch clock, unlocks eligible tests, and builds exam triage plan
    suspend fun loadPwDashboard(): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        seedInitialBatchIfEmpty()

        val todayEpoch = LocalDate.now().toEpochDay()
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }

        val slots = dao.getPwBatchSlots()
        val chapters = dao.getAllPwChapters()
        val clockOutcome = OfflineBatchClockEngine.advanceClock(slots, chapters, todayEpoch, nowMinute)
        if (clockOutcome.newlyAddedLectures > 0) {
            dao.upsertPwChapters(clockOutcome.updatedChapters)
            dao.upsertPwBatchSlots(clockOutcome.updatedSlots)
        }

        val freshChapters = dao.getAllPwChapters()
        val tests = dao.getRecentPwItems(30)
        val updatedTests = PwExamTriageEngine.evaluateMissedTestVault(tests, freshChapters, todayEpoch)
        if (updatedTests != tests) {
            dao.upsertPwItems(updatedTests)
        }

        val examDaysAhead = prefs.getInt(KEY_EXAM_DAYS, 45).coerceAtLeast(3)
        val dailyStudyHours = prefs.getFloat(KEY_DAILY_STUDY_HRS, 4.0f).coerceAtLeast(1.0f)

        val plan = PwExamTriageEngine.buildExamTriagePlan(
            chapters = freshChapters,
            todayEpochDay = todayEpoch,
            examDateEpochDay = todayEpoch + examDaysAhead,
            dailySelfStudyHours = dailyStudyHours
        )
        dao.insertPwStudyTasks(OfflineBatchClockEngine.generateDayByDayPlan(plan.items, todayEpoch, plan.daysRemaining))

        val passivePrefs = appContext.getSharedPreferences("pw_passive_sync", Context.MODE_PRIVATE)
        val lastSeenMs = passivePrefs.getLong("last_pw_lecture_seen_ms", 0L)
        val lastSyncedMs = prefs.getLong(KEY_LAST_PASSIVE_SYNC_MS, 0L)
        val pendingPassive = if (lastSeenMs > lastSyncedMs) 1 else 0

        PwDashboardSnapshot(
            noticesAndTests = dao.getRecentPwItems(30),
            chapters = freshChapters,
            triagePlan = plan,
            examDaysAhead = examDaysAhead,
            dailySelfStudyHours = dailyStudyHours,
            passiveSyncPendingCount = pendingPassive,
            batchClockAddedLectures = clockOutcome.newlyAddedLectures,
            studyTasks = dao.getPwStudyTasks(todayEpoch)
        )
    }

    // Updates exam countdown and daily study budget used by Exam Triage Engine
    suspend fun updateExamTarget(daysAhead: Int, dailyHours: Float): PwDashboardSnapshot {
        prefs.edit()
            .putInt(KEY_EXAM_DAYS, daysAhead.coerceIn(3, 365))
            .putFloat(KEY_DAILY_STUDY_HRS, dailyHours.coerceIn(1f, 14f))
            .apply()
        return loadPwDashboard()
    }

    // Completes one lecture and DPP for a chapter and awards +30 PW Block RP
    suspend fun markChapterProgress(chapterId: String, completeDpp: Boolean): PwDashboardSnapshot =
        withContext(Dispatchers.IO) {
            val chapter = dao.getAllPwChapters().firstOrNull { it.chapterId == chapterId }
                ?: return@withContext loadPwDashboard()
            val nextCompleted = (chapter.completedLectures + 1).coerceAtMost(chapter.totalBatchLectures)
            dao.upsertPwChapter(
                chapter.copy(
                    completedLectures = nextCompleted,
                    dppCompleted = chapter.dppCompleted || completeDpp
                )
            )
            dao.addRpEvent(
                RpEventEntity(
                    timestamp = System.currentTimeMillis(),
                    source = RpSource.PW_BLOCK.name,
                    rpDelta = RpSource.PW_BLOCK.rpDelta
                )
            )
            loadPwDashboard()
        }

    // Claims +30 RP from passive AccessibilityService PW screen sync
    suspend fun claimPassiveScreenSync(): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        val passivePrefs = appContext.getSharedPreferences("pw_passive_sync", Context.MODE_PRIVATE)
        val lastSeenMs = passivePrefs.getLong("last_pw_lecture_seen_ms", 0L)
        prefs.edit().putLong(KEY_LAST_PASSIVE_SYNC_MS, lastSeenMs.coerceAtLeast(System.currentTimeMillis())).apply()
        dao.addRpEvent(
            RpEventEntity(
                timestamp = System.currentTimeMillis(),
                source = RpSource.PW_BLOCK.name,
                rpDelta = RpSource.PW_BLOCK.rpDelta
            )
        )
        loadPwDashboard()
    }

    // Imports custom batch syllabus rows from CSV text
    suspend fun importBatchCsv(csvText: String): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        val parsed = OfflineBatchClockEngine.parseBatchCsv(csvText)
        if (parsed.isNotEmpty()) {
            dao.upsertPwChapters(parsed)
        }
        loadPwDashboard()
    }

    // Replaces syllabus with an offline preset without contacting Physics Wallah services
    suspend fun applyPreset(name: String): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        dao.clearPwChapters()
        dao.upsertPwChapters(OfflineBatchClockEngine.samplePreset(name))
        loadPwDashboard()
    }

    // Completes a daily checklist task once and awards the standard +30 PW Block RP
    suspend fun completeStudyTask(taskId: String): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        val task = dao.getPwStudyTask(taskId)
        if (task != null && !task.isCompleted) {
            dao.upsertPwStudyTask(task.copy(isCompleted = true))
            dao.addRpEvent(RpEventEntity(timestamp = System.currentTimeMillis(), source = RpSource.PW_BLOCK.name, rpDelta = RpSource.PW_BLOCK.rpDelta))
        }
        loadPwDashboard()
    }

    // Marks an unlocked PW test completed
    suspend fun completeTest(itemId: String): PwDashboardSnapshot = withContext(Dispatchers.IO) {
        val item = dao.getRecentPwItems(30).firstOrNull { it.itemId == itemId }
            ?: return@withContext loadPwDashboard()
        if (item.isTestUnlocked && !item.isTestCompleted) {
            dao.upsertPwItem(item.copy(isTestCompleted = true))
            dao.addRpEvent(
                RpEventEntity(
                    timestamp = System.currentTimeMillis(),
                    source = RpSource.PW_BLOCK.name,
                    rpDelta = RpSource.PW_BLOCK.rpDelta
                )
            )
        }
        loadPwDashboard()
    }

    // Seeds initial PW chapters, weekly batch clock slots, and a sample Missed Test Vault item
    private suspend fun seedInitialBatchIfEmpty() {
        if (dao.getAllPwChapters().isEmpty()) {
            val sampleChapters = listOf(
                PwSyllabusEntity(
                    chapterId = "phy_rotational",
                    subjectName = "Physics",
                    chapterName = "Rotational Motion",
                    totalBatchLectures = 9,
                    completedLectures = 3,
                    difficultyWeightageTier = "TIER1_HARD_FULL_BATCH",
                    dppCompleted = false
                ),
                PwSyllabusEntity(
                    chapterId = "chem_thermo",
                    subjectName = "Chemistry",
                    chapterName = "Chemical Thermodynamics",
                    totalBatchLectures = 7,
                    completedLectures = 2,
                    difficultyWeightageTier = "TIER2_MEDIUM_ONESHOT",
                    dppCompleted = false
                ),
                PwSyllabusEntity(
                    chapterId = "bio_genetics",
                    subjectName = "Biology",
                    chapterName = "Molecular Basis of Inheritance",
                    totalBatchLectures = 5,
                    completedLectures = 4,
                    difficultyWeightageTier = "TIER3_EASY_NOTES_PYQ",
                    dppCompleted = false
                )
            )
            dao.upsertPwChapters(sampleChapters)
            dao.upsertPwBatchSlots(
                listOf(
                    PwBatchSlotEntity("slot_mon_phy", "Physics", "phy_rotational", 1, 1140),
                    PwBatchSlotEntity("slot_wed_chem", "Chemistry", "chem_thermo", 3, 1140),
                    PwBatchSlotEntity("slot_fri_bio", "Biology", "bio_genetics", 5, 1140)
                )
            )
        }
        if (dao.getRecentPwItems(5).isEmpty()) {
            dao.upsertPwItem(
                PwNoticeTestEntity(
                    itemId = "pw_seed_test_1",
                    itemType = PwItemType.TEST_SCHEDULED.name,
                    title = "AITS Sunday Minor Test #4 (Locked until syllabus ready)",
                    rawNotificationText = "Requires Rotational Motion + Molecular Inheritance",
                    timestampEpochMillis = System.currentTimeMillis(),
                    requiredChapterIdsJson = "phy_rotational,bio_genetics",
                    isTestUnlocked = false
                )
            )
        }
    }

    companion object {
        private const val KEY_EXAM_DAYS = "pw_exam_days_ahead"
        private const val KEY_DAILY_STUDY_HRS = "pw_daily_study_hrs"
        private const val KEY_LAST_PASSIVE_SYNC_MS = "pw_last_passive_sync_ms"
    }
}
