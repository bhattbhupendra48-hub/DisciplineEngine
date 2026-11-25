package com.bhupendra.disciplineengine.data

import java.time.LocalDate
import java.time.LocalTime

// Lightweight zero-dependency helper for 24-hour analytics arrays in Room
object AnalyticsRecorder {

    // Parses a JSON array string like "[0,1,2,...]" into a fixed 24-element array
    fun parseHourlyArray(json: String?): IntArray {
        val out = IntArray(24)
        if (json.isNullOrBlank()) return out
        val parts = json.removePrefix("[").removeSuffix("]").split(",")
        for (i in 0 until minOf(24, parts.size)) {
            out[i] = parts[i].trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
        return out
    }

    // Serializes a 24-element int array into compact JSON
    fun serializeHourlyArray(values: IntArray): String {
        return values.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    // Increments the blocked temptation count for the current hour in Room
    suspend fun recordBlockedAttempt(dao: DisciplineDao) {
        val today = LocalDate.now().toEpochDay()
        val hour = LocalTime.now().hour.coerceIn(0, 23)
        val existing = dao.getAnalyticsForDay(today) ?: DailyAnalyticsEntity(dateEpochDay = today)
        val arr = parseHourlyArray(existing.blockedReelsAttemptsByHourJson)
        arr[hour] = arr[hour] + 1
        dao.upsertDailyAnalytics(existing.copy(blockedReelsAttemptsByHourJson = serializeHourlyArray(arr)))
    }

    // Adds completed study minutes to the current hour bucket in Room
    suspend fun recordStudyMinutes(dao: DisciplineDao, minutes: Int) {
        if (minutes <= 0) return
        val today = LocalDate.now().toEpochDay()
        val hour = LocalTime.now().hour.coerceIn(0, 23)
        val existing = dao.getAnalyticsForDay(today) ?: DailyAnalyticsEntity(dateEpochDay = today)
        val arr = parseHourlyArray(existing.deepStudyMinutesByHourJson)
        arr[hour] = arr[hour] + minutes
        dao.upsertDailyAnalytics(existing.copy(deepStudyMinutesByHourJson = serializeHourlyArray(arr)))
    }
}
