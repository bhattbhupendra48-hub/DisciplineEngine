package com.bhupendra.disciplineengine.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.alarm.AlarmForegroundService
import com.bhupendra.disciplineengine.alarm.AlarmScheduler
import com.bhupendra.disciplineengine.alarm.DirectBootAlarmStore
import com.bhupendra.disciplineengine.camera.CameraVerifyActivity
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.DisciplineRepository
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch
import java.util.Locale

// Dedicated Pushup Arena & Kill-Proof Camera Alarm management module
@Composable
fun PushupArenaScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DirectBootAlarmStore(context) }
    val repository = remember {
        DisciplineRepository(DisciplineDatabase.get(DisciplineApp.appContext).dao())
    }

    var alarmEnabled by remember { mutableStateOf(store.isAlarmEnabled()) }
    var alarmHour by remember { mutableIntStateOf(store.alarmHour()) }
    var alarmMinute by remember { mutableIntStateOf(store.alarmMinute()) }
    var strictMode by remember { mutableStateOf(true) }
    var tokensLeft by remember { mutableIntStateOf(2) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        tokensLeft = DisciplineDatabase.get(DisciplineApp.appContext).dao().getStreak()
            ?.monthlyDarkRoomBypassTokensLeft ?: 2
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val reps = result.data?.getIntExtra(CameraVerifyActivity.EXTRA_VERIFIED_REPS, 20) ?: 20
            scope.launch {
                repository.recordVerifiedPushups(reps)
                feedback = "Verified $reps reps! Dopamine Debt / Reels Bank updated."
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(borderAccent = P1CriticalNeon) {
                Text(
                    text = "PUSHUP ARENA (BLAZEPOSE-LITE)",
                    color = CyberCyan,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Prop phone against wall showing full side profile. Runs 640x480 @ 15 FPS only while open.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mode: ${if (strictMode) "Strict Plank (165-195 deg)" else "Beginner Plank (145-200 deg)"}",
                    color = XpRankGold
                )
                Text("Monthly Dark-Room Squat Tokens: $tokensLeft / 2", color = VerifiedEmerald)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        cameraLauncher.launch(
                            Intent(context, CameraVerifyActivity::class.java).apply {
                                putExtra(CameraVerifyActivity.EXTRA_TARGET_REPS, 20)
                                putExtra(CameraVerifyActivity.EXTRA_STRICT_MODE, strictMode)
                            }
                        )
                    }) {
                        Text("Start 20 Pushups")
                    }
                    OutlinedButton(onClick = { strictMode = !strictMode }) {
                        Text(if (strictMode) "Switch Beginner" else "Switch Strict")
                    }
                }
                if (feedback != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(feedback!!, color = VerifiedEmerald, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            val timeStr = String.format(Locale.US, "%02d:%02d", alarmHour, alarmMinute)
            GlassCard(borderAccent = CyberCyan) {
                Text("Kill-Proof Alarm & 3x3 Burst Config", color = CyberCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (alarmEnabled) "Armed Daily at $timeStr" else "Disarmed ($timeStr)",
                    color = if (alarmEnabled) VerifiedEmerald else XpRankGold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val totalMins = (alarmHour * 60 + alarmMinute - 15 + 1440) % 1440
                        alarmHour = totalMins / 60
                        alarmMinute = totalMins % 60
                        if (alarmEnabled) AlarmScheduler.scheduleDailyAlarm(context, alarmHour, alarmMinute)
                    }) { Text("-15m") }
                    OutlinedButton(onClick = {
                        val totalMins = (alarmHour * 60 + alarmMinute + 15) % 1440
                        alarmHour = totalMins / 60
                        alarmMinute = totalMins % 60
                        if (alarmEnabled) AlarmScheduler.scheduleDailyAlarm(context, alarmHour, alarmMinute)
                    }) { Text("+15m") }
                    Button(onClick = {
                        if (alarmEnabled) {
                            AlarmScheduler.cancelDailyAlarm(context)
                            alarmEnabled = false
                        } else {
                            AlarmScheduler.scheduleDailyAlarm(context, alarmHour, alarmMinute)
                            alarmEnabled = true
                        }
                    }) { Text(if (alarmEnabled) "Disarm" else "Arm Alarm") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startForegroundService(
                            Intent(context, AlarmForegroundService::class.java).apply {
                                action = AlarmForegroundService.ACTION_START_ALARM
                            }
                        )
                    }
                ) {
                    Text("Launch Test Alarm (Lockscreen + Volume Guard)")
                }
            }
        }
    }
}
