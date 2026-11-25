package com.bhupendra.disciplineengine.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Direct-Boot receiver that resurrects alarms on lockscreen immediately after reboot
class DirectBootAlarmReceiver : BroadcastReceiver() {

    // Handles scheduled alarm triggers, recents relaunches, and pre-unlock boot events
    override fun onReceive(context: Context, intent: Intent) {
        val store = DirectBootAlarmStore(context)
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                if (store.isRingingActive()) {
                    startAlarmService(context)
                } else if (store.isAlarmEnabled()) {
                    AlarmScheduler.scheduleDailyAlarm(
                        context,
                        store.alarmHour(),
                        store.alarmMinute()
                    )
                }
            }
            AlarmScheduler.ACTION_ALARM_TRIGGER,
            AlarmScheduler.ACTION_RELAUNCH_ALARM -> {
                startAlarmService(context)
            }
        }
    }

    // Starts the foreground alarm service safely across Android API levels
    private fun startAlarmService(context: Context) {
        val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START_ALARM
        }
        context.startForegroundService(serviceIntent)
    }
}
