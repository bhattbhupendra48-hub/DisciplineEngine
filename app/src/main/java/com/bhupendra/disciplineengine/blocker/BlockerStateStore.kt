package com.bhupendra.disciplineengine.blocker

import android.content.Context
import java.time.LocalDate

// Persists blocker counters, ethical settings unlock window, and daily blocked temptation totals
class BlockerStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Increments today's blocked temptation counter and returns the updated count
    fun incrementBlockedToday(): Int {
        val today = LocalDate.now().toEpochDay()
        val storedDay = prefs.getLong(KEY_DAY, -1L)
        val count = if (storedDay == today) prefs.getInt(KEY_BLOCKED_COUNT, 0) + 1 else 1
        prefs.edit()
            .putLong(KEY_DAY, today)
            .putInt(KEY_BLOCKED_COUNT, count)
            .apply()
        return count
    }

    // Returns today's blocked temptation count
    fun getBlockedToday(): Int {
        val today = LocalDate.now().toEpochDay()
        return if (prefs.getLong(KEY_DAY, -1L) == today) prefs.getInt(KEY_BLOCKED_COUNT, 0) else 0
    }

    // Grants temporary 5-minute Settings access after completing ethical cooldown or 50 pushups
    fun grantEthicalSettingsUnlock(durationMs: Long = 5 * 60 * 1000L) {
        prefs.edit().putLong(KEY_ETHICAL_UNLOCK_UNTIL, System.currentTimeMillis() + durationMs).apply()
    }

    // Returns true while the temporary ethical Settings unlock window is valid
    fun isEthicalSettingsUnlockActive(): Boolean {
        return System.currentTimeMillis() < prefs.getLong(KEY_ETHICAL_UNLOCK_UNTIL, 0L)
    }

    companion object {
        private const val PREFS_NAME = "discipline_blocker_prefs"
        private const val KEY_DAY = "blocked_epoch_day"
        private const val KEY_BLOCKED_COUNT = "blocked_count_today"
        private const val KEY_ETHICAL_UNLOCK_UNTIL = "ethical_unlock_until"
    }
}
