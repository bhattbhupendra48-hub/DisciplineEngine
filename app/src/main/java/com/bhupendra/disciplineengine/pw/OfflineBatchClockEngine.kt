package com.bhupendra.disciplineengine.pw

import com.bhupendra.disciplineengine.data.PwBatchSlotEntity
import com.bhupendra.disciplineengine.data.PwSyllabusEntity
import com.bhupendra.disciplineengine.data.PwStudyTaskEntity
import java.net.URLEncoder
import java.time.LocalDate

// Result of ticking the offline batch syllabus clock against today's class times
data class BatchClockTickOutcome(
    val updatedChapters: List<PwSyllabusEntity>,
    val updatedSlots: List<PwBatchSlotEntity>,
    val newlyAddedLectures: Int
)

object OfflineBatchClockEngine {

    // Creates stable daily checklist tasks spread evenly across the remaining exam window
    fun generateDayByDayPlan(items: List<ChapterTriageItem>, todayEpochDay: Long, daysRemaining: Long): List<PwStudyTaskEntity> {
        val days = daysRemaining.coerceAtLeast(1L)
        return items.mapIndexed { index, item ->
            val strategy = item.strategy.name
            val query = URLEncoder.encode("${item.chapter.subjectName} ${item.chapter.chapterName} one shot", "UTF-8")
            val date = todayEpochDay + (index.toLong() % days)
            PwStudyTaskEntity(
                taskId = "${date}_${item.chapter.chapterId}_$strategy",
                dateEpochDay = date,
                chapterId = item.chapter.chapterId,
                title = "${item.chapter.subjectName}: ${item.chapter.chapterName} • ${item.strategy.label}",
                strategy = strategy,
                estimatedMinutes = item.estimatedMinutes,
                youtubeSearchUrl = "https://www.youtube.com/results?search_query=$query"
            )
        }
    }

    // Supplies offline sample chapter rows for supported NEET and JEE batch presets
    fun samplePreset(name: String): List<PwSyllabusEntity> {
        val rows = when (name) {
            "NEET 2.0" -> listOf(
                Triple("Physics", "Laws of Motion", "TIER1_HARD_FULL_BATCH"),
                Triple("Chemistry", "Chemical Bonding", "TIER1_HARD_FULL_BATCH"),
                Triple("Biology", "Cell: The Unit of Life", "TIER3_EASY_NOTES_PYQ"),
                Triple("Biology", "Principles of Inheritance", "TIER2_MEDIUM_ONESHOT")
            )
            else -> listOf(
                Triple("Physics", "Rotational Motion", "TIER1_HARD_FULL_BATCH"),
                Triple("Chemistry", "Electrochemistry", "TIER2_MEDIUM_ONESHOT"),
                Triple("Mathematics", "Limits and Derivatives", "TIER1_HARD_FULL_BATCH"),
                Triple("Mathematics", "Statistics", "TIER3_EASY_NOTES_PYQ")
            )
        }
        return rows.mapIndexed { index, row ->
            PwSyllabusEntity("preset_${name.lowercase().replace(' ', '_')}_$index", row.first, row.second, 6, 0, difficultyWeightageTier = row.third)
        }
    }

    // Advances pending PW lecture counts once scheduled class times pass without touching PW servers
    fun advanceClock(
        slots: List<PwBatchSlotEntity>,
        chapters: List<PwSyllabusEntity>,
        todayEpochDay: Long,
        currentMinuteOfDay: Int
    ): BatchClockTickOutcome {
        val dayOfWeek = LocalDate.ofEpochDay(todayEpochDay).dayOfWeek.value
        val chapterMap = chapters.associateBy { it.chapterId }.toMutableMap()
        val updatedSlots = mutableListOf<PwBatchSlotEntity>()
        var addedCount = 0

        for (slot in slots) {
            val isTodaySlot = slot.dayOfWeekValue == dayOfWeek
            val classPassed = currentMinuteOfDay >= slot.classTimeMinutes
            val notYetProcessedToday = slot.lastProcessedEpochDay != todayEpochDay

            if (isTodaySlot && classPassed && notYetProcessedToday) {
                val chapter = chapterMap[slot.linkedChapterId]
                if (chapter != null) {
                    chapterMap[chapter.chapterId] = chapter.copy(
                        totalBatchLectures = chapter.totalBatchLectures + 1
                    )
                    addedCount += 1
                }
                updatedSlots.add(slot.copy(lastProcessedEpochDay = todayEpochDay))
            } else {
                updatedSlots.add(slot)
            }
        }

        return BatchClockTickOutcome(
            updatedChapters = chapterMap.values.toList(),
            updatedSlots = updatedSlots,
            newlyAddedLectures = addedCount
        )
    }

    // Parses lightweight CSV batch lines (Subject,Chapter,TotalLectures,Completed,Tier)
    fun parseBatchCsv(csvText: String): List<PwSyllabusEntity> {
        return csvText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("subject,chapter", ignoreCase = true) }
            .mapIndexedNotNull { idx, line ->
                val cols = line.split(",", "|", "\t").map { it.trim().trim('"') }
                if (cols.size < 3) return@mapIndexedNotNull null
                val subject = cols[0]
                val chapter = cols[1]
                val total = cols[2].toIntOrNull()?.coerceAtLeast(1) ?: 6
                val completed = cols.getOrNull(3)?.toIntOrNull()?.coerceIn(0, total) ?: 0
                val tier = cols.getOrNull(4)?.takeIf {
                    it in listOf("TIER1_HARD_FULL_BATCH", "TIER2_MEDIUM_ONESHOT", "TIER3_EASY_NOTES_PYQ")
                } ?: "TIER2_MEDIUM_ONESHOT"
                PwSyllabusEntity(
                    chapterId = "csv_${subject.lowercase()}_${idx}_${chapter.hashCode()}",
                    subjectName = subject,
                    chapterName = chapter,
                    totalBatchLectures = total,
                    completedLectures = completed,
                    difficultyWeightageTier = tier
                )
            }
            .toList()
    }
}
