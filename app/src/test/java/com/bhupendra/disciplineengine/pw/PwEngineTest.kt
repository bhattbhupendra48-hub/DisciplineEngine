package com.bhupendra.disciplineengine.pw

import com.bhupendra.disciplineengine.data.PwBatchSlotEntity
import com.bhupendra.disciplineengine.data.PwNoticeTestEntity
import com.bhupendra.disciplineengine.data.PwSyllabusEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PwEngineTest {

    // Verifies regex classifier separates PW live classes, DPPs, tests, and notices
    @Test
    fun noticeParserClassifiesAllFourCategories() {
        assertEquals(PwItemType.LIVE_CLASS_ALERT, PwNoticeParser.parse("Physics Class Starting", "Join live now").itemType)
        assertEquals(PwItemType.DPP_UPLOAD, PwNoticeParser.parse("DPP #4 Uploaded", "Electrostatics worksheet").itemType)
        assertEquals(PwItemType.TEST_SCHEDULED, PwNoticeParser.parse("AITS Sunday Test", "Syllabus announced").itemType)
        assertEquals(PwItemType.BATCH_NOTICE, PwNoticeParser.parse("Holiday Notice", "Batch rescheduled").itemType)
    }

    // Verifies Exam Triage Engine switches to Hard 1.5x / Medium OneShot / Easy Notes when time is tight
    @Test
    fun examTriageAssignsCorrectStrategiesUnderTimePressure() {
        val chapters = listOf(
            PwSyllabusEntity("c1", "Physics", "Rotational", 10, 0, difficultyWeightageTier = "TIER1_HARD_FULL_BATCH"),
            PwSyllabusEntity("c2", "Chemistry", "Thermo", 10, 0, difficultyWeightageTier = "TIER2_MEDIUM_ONESHOT"),
            PwSyllabusEntity("c3", "Biology", "Ecology", 8, 0, difficultyWeightageTier = "TIER3_EASY_NOTES_PYQ")
        )
        val plan = PwExamTriageEngine.buildExamTriagePlan(
            chapters = chapters,
            todayEpochDay = 100L,
            examDateEpochDay = 105L, // only 5 days left -> 20h available vs 42h full batch
            dailySelfStudyHours = 4f
        )
        assertTrue(plan.needsTriageCompression)
        assertEquals(TriageStrategy.FULL_BATCH_1_5X, plan.items[0].strategy)
        assertEquals(TriageStrategy.ONESHOT_2H_PRACTICE, plan.items[1].strategy)
        assertEquals(TriageStrategy.NOTES_AND_PYQ, plan.items[2].strategy)
    }

    // Verifies Missed Test Vault unlocks test into next Sunday slot once syllabus finishes
    @Test
    fun missedTestVaultUnlocksWhenRequiredChaptersFinish() {
        val incomplete = listOf(
            PwSyllabusEntity("c1", "Physics", "Rotational", 5, 3, difficultyWeightageTier = "TIER1_HARD_FULL_BATCH", dppCompleted = false)
        )
        val testItem = PwNoticeTestEntity(
            itemId = "t1",
            itemType = PwItemType.TEST_SCHEDULED.name,
            title = "Minor Test",
            rawNotificationText = "",
            timestampEpochMillis = 1000L,
            requiredChapterIdsJson = "c1",
            isTestUnlocked = false
        )
        val stillLocked = PwExamTriageEngine.evaluateMissedTestVault(listOf(testItem), incomplete, 20000L)
        assertFalse(stillLocked.first().isTestUnlocked)

        val completed = listOf(incomplete.first().copy(completedLectures = 5, dppCompleted = true))
        val unlocked = PwExamTriageEngine.evaluateMissedTestVault(listOf(testItem), completed, 20000L)
        assertTrue(unlocked.first().isTestUnlocked)
        val slotDay = unlocked.first().scheduledSundaySlotDate!!
        assertEquals(DayOfWeek.SUNDAY, LocalDate.ofEpochDay(slotDay).dayOfWeek)
    }

    // Verifies Offline Batch Clock advances lecture count once per day after class time passes
    @Test
    fun offlineBatchClockAdvancesOnlyOnceAfterClassTime() {
        val today = LocalDate.of(2026, 11, 25) // Wednesday = 3
        val epochDay = today.toEpochDay()
        val slots = listOf(PwBatchSlotEntity("s1", "Chemistry", "c2", today.dayOfWeek.value, 1140, null))
        val chapters = listOf(PwSyllabusEntity("c2", "Chemistry", "Thermo", 5, 2, difficultyWeightageTier = "TIER2_MEDIUM_ONESHOT"))

        val beforeClass = OfflineBatchClockEngine.advanceClock(slots, chapters, epochDay, 1000)
        assertEquals(0, beforeClass.newlyAddedLectures)

        val afterClass = OfflineBatchClockEngine.advanceClock(slots, chapters, epochDay, 1150)
        assertEquals(1, afterClass.newlyAddedLectures)
        assertEquals(6, afterClass.updatedChapters.first().totalBatchLectures)

        val repeatSameDay = OfflineBatchClockEngine.advanceClock(afterClass.updatedSlots, afterClass.updatedChapters, epochDay, 1200)
        assertEquals(0, repeatSameDay.newlyAddedLectures)
    }

    // Verifies syllabus presets and generated One-Shot tasks are offline and link to YouTube search
    @Test
    fun presetsAndDailyTasksAreGeneratedWithSearchLinks() {
        val preset = OfflineBatchClockEngine.samplePreset("NEET 2.0")
        assertTrue(preset.any { it.subjectName == "Biology" })
        assertEquals(4, preset.size)
        val chapter = preset.first { it.difficultyWeightageTier == "TIER2_MEDIUM_ONESHOT" }
        val task = OfflineBatchClockEngine.generateDayByDayPlan(
            listOf(ChapterTriageItem(chapter, TriageStrategy.ONESHOT_2H_PRACTICE, 150)),
            todayEpochDay = 20_000L,
            daysRemaining = 10L
        ).single()
        assertTrue(task.youtubeSearchUrl.startsWith("https://www.youtube.com/results?search_query="))
        assertEquals(20_000L, task.dateEpochDay)
    }

    // Verifies pasted plain text syllabus rows parse into local chapter records
    @Test
    fun pastedSyllabusRowsParseWithoutNetwork() {
        val parsed = OfflineBatchClockEngine.parseBatchCsv("Physics|Electrostatics|8|2|TIER1_HARD_FULL_BATCH")
        assertEquals("Electrostatics", parsed.single().chapterName)
        assertEquals(2, parsed.single().completedLectures)
    }
}
