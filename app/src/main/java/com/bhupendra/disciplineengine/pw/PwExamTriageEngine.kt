package com.bhupendra.disciplineengine.pw

import com.bhupendra.disciplineengine.data.PwNoticeTestEntity
import com.bhupendra.disciplineengine.data.PwSyllabusEntity
import java.time.DayOfWeek
import java.time.LocalDate

// Recommended study format per chapter computed by the Exam Triage Engine
enum class TriageStrategy(val label: String) {
    FULL_BATCH_1_0X("Full Batch Lecture + DPP"),
    FULL_BATCH_1_5X("Hard Tier: Full Batch @ 1.5x + DPP"),
    ONESHOT_2H_PRACTICE("Medium Tier: 2h One-Shot + 30m Practice"),
    NOTES_AND_PYQ("Easy Tier: Class Notes PDF + NCERT/PYQs")
}

// Per-chapter recommendation entry shown in the Exam Triage Matrix
data class ChapterTriageItem(
    val chapter: PwSyllabusEntity,
    val strategy: TriageStrategy,
    val estimatedMinutes: Int
)

// Overall backlog feasibility and triaged plan summary before the user's exam date
data class ExamTriagePlan(
    val daysRemaining: Long,
    val availableSelfStudyMinutes: Long,
    val fullBatchRequiredMinutes: Long,
    val triagedRequiredMinutes: Long,
    val needsTriageCompression: Boolean,
    val items: List<ChapterTriageItem>
)

object PwExamTriageEngine {

    // Evaluates pending PW chapters against available hours and creates a triage plan
    fun buildExamTriagePlan(
        chapters: List<PwSyllabusEntity>,
        todayEpochDay: Long,
        examDateEpochDay: Long,
        dailySelfStudyHours: Float
    ): ExamTriagePlan {
        val daysLeft = (examDateEpochDay - todayEpochDay).coerceAtLeast(1L)
        val availableMins = (daysLeft * dailySelfStudyHours * 60f).toLong()

        val pending = chapters.filter {
            it.isIncludedInUpcomingExam && (it.completedLectures < it.totalBatchLectures || !it.dppCompleted)
        }

        val fullBatchMins = pending.sumOf { ch ->
            val remLectures = (ch.totalBatchLectures - ch.completedLectures).coerceAtLeast(0)
            (remLectures * ch.avgLectureDurationMinutes).toLong() + if (ch.dppCompleted) 0L else 45L
        }

        val needsTriage = fullBatchMins > availableMins
        val items = pending.map { ch ->
            val remLectures = (ch.totalBatchLectures - ch.completedLectures).coerceAtLeast(0)
            if (!needsTriage) {
                ChapterTriageItem(
                    chapter = ch,
                    strategy = TriageStrategy.FULL_BATCH_1_0X,
                    estimatedMinutes = remLectures * ch.avgLectureDurationMinutes
                )
            } else {
                when (ch.difficultyWeightageTier) {
                    "TIER1_HARD_FULL_BATCH" -> {
                        val at15x = ((remLectures * ch.avgLectureDurationMinutes) * 2) / 3
                        ChapterTriageItem(
                            chapter = ch,
                            strategy = TriageStrategy.FULL_BATCH_1_5X,
                            estimatedMinutes = at15x.coerceAtLeast(90)
                        )
                    }
                    "TIER2_MEDIUM_ONESHOT" -> ChapterTriageItem(
                        chapter = ch,
                        strategy = TriageStrategy.ONESHOT_2H_PRACTICE,
                        estimatedMinutes = ch.oneShotDurationMinutes + 30
                    )
                    else -> ChapterTriageItem(
                        chapter = ch,
                        strategy = TriageStrategy.NOTES_AND_PYQ,
                        estimatedMinutes = ch.notesAndPyqDurationMinutes
                    )
                }
            }
        }

        val triagedMins = items.sumOf { it.estimatedMinutes.toLong() }
        return ExamTriagePlan(
            daysRemaining = daysLeft,
            availableSelfStudyMinutes = availableMins,
            fullBatchRequiredMinutes = fullBatchMins,
            triagedRequiredMinutes = triagedMins,
            needsTriageCompression = needsTriage,
            items = items
        )
    }

    // Evaluates Missed Test Vault items and unlocks tests once prerequisite chapters finish
    fun evaluateMissedTestVault(
        tests: List<PwNoticeTestEntity>,
        chapters: List<PwSyllabusEntity>,
        todayEpochDay: Long
    ): List<PwNoticeTestEntity> {
        val completedIds = chapters
            .filter { it.completedLectures >= it.totalBatchLectures && it.dppCompleted }
            .map { it.chapterId }
            .toSet()

        val nextSunday = findNextSundayEpochDay(todayEpochDay)

        return tests.map { item ->
            if (item.itemType != PwItemType.TEST_SCHEDULED.name || item.isTestCompleted) {
                item
            } else {
                val requiredIds = parseRequiredIds(item.requiredChapterIdsJson)
                val ready = requiredIds.isEmpty() || requiredIds.all { it in completedIds }
                if (ready && !item.isTestUnlocked) {
                    item.copy(
                        isTestUnlocked = true,
                        scheduledSundaySlotDate = item.scheduledSundaySlotDate ?: nextSunday
                    )
                } else {
                    item
                }
            }
        }
    }

    // Parses lightweight comma/JSON chapter ID lists without Gson overhead
    fun parseRequiredIds(jsonOrCsv: String): List<String> {
        return jsonOrCsv
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
    }

    // Computes the epoch day for the upcoming Sunday Test Window
    fun findNextSundayEpochDay(todayEpochDay: Long): Long {
        var date = LocalDate.ofEpochDay(todayEpochDay)
        if (date.dayOfWeek == DayOfWeek.SUNDAY) return todayEpochDay
        while (date.dayOfWeek != DayOfWeek.SUNDAY) {
            date = date.plusDays(1)
        }
        return date.toEpochDay()
    }
}
