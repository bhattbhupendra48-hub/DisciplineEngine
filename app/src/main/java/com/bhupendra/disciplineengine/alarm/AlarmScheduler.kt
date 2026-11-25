package com.bhupendra.disciplineengine.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_ALARM_TRIGGER = "com.bhupendra.disciplineengine.ACTION_ALARM_TRIGGER"
    const val ACTION_RELAUNCH_ALARM = "com.bhupendra.disciplineengine.ACTION_RELAUNCH_ALARM"
    private const val ALARM_REQUEST_CODE = 3001
    private const val RELAUNCH_REQUEST_CODE = 3002

    // Schedules the next daily Doze-proof alarm clock using AlarmManager
    fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int) {
        val store = DirectBootAlarmStore(context)
        store.saveSchedule(hour, minute, enabled = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerIntent = Intent(context, DirectBootAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = computeNextTriggerMillis(hour, minute)
        val showIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, AlarmVerifyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }

        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)
    }

    // Cancels the scheduled daily alarm clock and clears enabled state
    fun cancelDailyAlarm(context: Context) {
        val store = DirectBootAlarmStore(context)
        store.saveSchedule(store.alarmHour(), store.alarmMinute(), enabled = false)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, DirectBootAlarmReceiver::class.java).apply { action = ACTION_ALARM_TRIGGER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    // Schedules an immediate 150ms relaunch broadcast when user swipes recents
    fun scheduleImmediateRelaunch(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, DirectBootAlarmReceiver::class.java).apply {
            action = ACTION_RELAUNCH_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RELAUNCH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val atMs = System.currentTimeMillis() + 150L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pendingIntent)
        }
    }

    // Computes the next future epoch timestamp matching the configured hour and minute
    fun computeNextTriggerMillis(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return cal.timeInMillis
    }
}
