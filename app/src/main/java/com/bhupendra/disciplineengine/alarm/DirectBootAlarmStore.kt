package com.bhupendra.disciplineengine.alarm

import android.content.Context
import android.content.SharedPreferences

// Persists alarm config in Device Encrypted Storage for Direct Boot lockscreen resurrection
class DirectBootAlarmStore(context: Context) {

    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Saves daily alarm hour, minute, and enabled flag
    fun saveSchedule(hour: Int, minute: Int, enabled: Boolean, requiredPushups: Int = 20) {
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_REQUIRED_PUSHUPS, requiredPushups)
            .apply()
    }

    // Marks whether an alarm is currently active so reboot resurrection restarts it
    fun setRingingActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_RINGING_ACTIVE, active).apply()
    }

    // Returns true if an alarm was ringing before shutdown or reboot
    fun isRingingActive(): Boolean = prefs.getBoolean(KEY_RINGING_ACTIVE, false)

    // Returns true if the daily morning alarm is enabled
    fun isAlarmEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    // Returns configured wake-up hour in 24h format
    fun alarmHour(): Int = prefs.getInt(KEY_HOUR, 5)

    // Returns configured wake-up minute
    fun alarmMinute(): Int = prefs.getInt(KEY_MINUTE, 30)

    // Returns required pushup count to dismiss the alarm
    fun requiredPushups(): Int = prefs.getInt(KEY_REQUIRED_PUSHUPS, 20)

    companion object {
        private const val PREFS_NAME = "discipline_direct_boot_alarm"
        private const val KEY_HOUR = "alarm_hour"
        private const val KEY_MINUTE = "alarm_minute"
        private const val KEY_ENABLED = "alarm_enabled"
        private const val KEY_RINGING_ACTIVE = "ringing_active"
        private const val KEY_REQUIRED_PUSHUPS = "required_pushups"
    }
}
