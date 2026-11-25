package com.bhupendra.disciplineengine.ui.screens

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.alarm.AlarmForegroundService
import com.bhupendra.disciplineengine.alarm.AlarmScheduler
import com.bhupendra.disciplineengine.alarm.DirectBootAlarmStore
import com.bhupendra.disciplineengine.data.DashboardSnapshot
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.DisciplineRepository
import com.bhupendra.disciplineengine.data.HabitEntity
import com.bhupendra.disciplineengine.domain.StreakEngine
import com.bhupendra.disciplineengine.domain.StreakStatus
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Renders the Phase 2 RPG dashboard in portrait or two-pane landscape layout
@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val repository = remember {
        DisciplineRepository(DisciplineDatabase.get(DisciplineApp.appContext).dao())
    }
    var snapshot by remember { mutableStateOf<DashboardSnapshot?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        snapshot = repository.loadDashboard()
    }

    val data = snapshot
    if (data == null) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Initializing Discipline Engine...", color = CyberCyan)
        }
        return
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RankBannerCard(
                        data = data,
                        onEquipTitle = { title ->
                            scope.launch { snapshot = repository.equipTitle(title) }
                        }
                    )
                }
                items(data.habits, key = { it.habitId }) { habit ->
                    HabitActionCard(habit = habit) {
                        scope.launch { snapshot = repository.completeHabit(habit.habitId) }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StreakAndShieldCard(
                        data = data,
                        feedbackMessage = feedbackMessage,
                        onBossRepair = {
                            scope.launch { snapshot = repository.attemptBossRepair(40, false) }
                        },
                        onUseShield = {
                            scope.launch { snapshot = repository.useEmergencyShield() }
                        },
                        onToggleIllness = {
                            scope.launch {
                                val ok = repository.tryEnableIllnessMode()
                                feedbackMessage = if (ok) {
                                    "Illness/Travel Mode ON — Streak frozen & Reels locked today."
                                } else {
                                    "Anti-Fake Sick Rule: Illness Mode can only be enabled before 10:00 AM."
                                }
                                snapshot = repository.loadDashboard()
                            }
                        }
                    )
                }
                item {
                    DopamineBankCard(
                        data = data,
                        onPayPushups = {
                            scope.launch { snapshot = repository.recordVerifiedPushups(20) }
                        },
                        onSimulateBypass = {
                            scope.launch { snapshot = repository.triggerEmergencyBypass() }
                        }
                    )
                }
                item { KillProofAlarmCard() }
                item { RecentRpLogCard(data) }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RankBannerCard(
                    data = data,
                    onEquipTitle = { title ->
                        scope.launch { snapshot = repository.equipTitle(title) }
                    }
                )
            }
            item {
                StreakAndShieldCard(
                    data = data,
                    feedbackMessage = feedbackMessage,
                    onBossRepair = {
                        scope.launch { snapshot = repository.attemptBossRepair(40, false) }
                    },
                    onUseShield = {
                        scope.launch { snapshot = repository.useEmergencyShield() }
                    },
                    onToggleIllness = {
                        scope.launch {
                            val ok = repository.tryEnableIllnessMode()
                            feedbackMessage = if (ok) {
                                "Illness/Travel Mode ON — Streak frozen & Reels locked today."
                            } else {
                                "Anti-Fake Sick Rule: Illness Mode can only be enabled before 10:00 AM."
                            }
                            snapshot = repository.loadDashboard()
                        }
                    }
                )
            }
            item {
                DopamineBankCard(
                    data = data,
                    onPayPushups = {
                        scope.launch { snapshot = repository.recordVerifiedPushups(20) }
                    },
                    onSimulateBypass = {
                        scope.launch { snapshot = repository.triggerEmergencyBypass() }
                    }
                )
            }
            item { KillProofAlarmCard() }
            items(data.habits, key = { it.habitId }) { habit ->
                HabitActionCard(habit = habit) {
                    scope.launch { snapshot = repository.completeHabit(habit.habitId) }
                }
            }
            item { RecentRpLogCard(data) }
        }
    }
}

// Displays RP rank tier progress, equipped title chips, and Ghost Rival delta
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankBannerCard(
    data: DashboardSnapshot,
    onEquipTitle: (String) -> Unit
) {
    val rank = data.rankProgress
    val state = data.streakState
    val ghostDelta = data.ghostRival.deltaPercent.roundToInt()
    val ghostColor = if (ghostDelta >= 0) VerifiedEmerald else P1CriticalNeon
    val ghostLabel = if (ghostDelta >= 0) "+$ghostDelta% vs Last Week Ghost" else "$ghostDelta% vs Last Week Ghost"

    GlassCard(borderAccent = XpRankGold) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DISCIPLINE ENGINE", color = CyberCyan, fontWeight = FontWeight.Bold)
            Text(ghostLabel, color = ghostColor, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${rank.currentTier.displayName.uppercase()} • ${rank.effectiveRp} RP",
            color = XpRankGold,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Title: ${state.equippedTitle}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { rank.progressFraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = XpRankGold,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Spacer(Modifier.height(4.dp))
        val nextLabel = rank.nextTier?.let { "${rank.rpToNextRank} RP to ${it.displayName}" } ?: "MAX RANK ACHIEVED"
        Text(nextLabel, color = CyberCyan, style = MaterialTheme.typography.labelSmall)

        if (data.unlockedTitles.size > 1) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.unlockedTitles.forEach { title ->
                    FilterChip(
                        selected = title == state.equippedTitle,
                        onClick = { onEquipTitle(title) },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

// Displays S2/S4/S5 streak status, Emergency Shields, and before-10-AM Illness Mode
@Composable
private fun StreakAndShieldCard(
    data: DashboardSnapshot,
    feedbackMessage: String?,
    onBossRepair: () -> Unit,
    onUseShield: () -> Unit,
    onToggleIllness: () -> Unit
) {
    val state = data.streakState
    val statusColor = when (state.streakStatus) {
        StreakStatus.ACTIVE_FLAME.name -> VerifiedEmerald
        StreakStatus.CRACKED_PENALTY_24H.name -> XpRankGold
        StreakStatus.FROZEN_SICK_TRAVEL.name -> CyberCyan
        else -> P1CriticalNeon
    }

    GlassCard(borderAccent = statusColor) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Streak: ${state.currentStreak}d (Best: ${state.bestStreak}d)",
                color = statusColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Shields: ${state.earnedEmergencyShields}/2",
                color = CyberCyan,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Status: ${state.streakStatus}",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isCracked) {
            val hoursLeft = StreakEngine.remainingGraceHours(state, System.currentTimeMillis())
            Spacer(Modifier.height(6.dp))
            Text(
                text = "CRACKED EMBLEM (${hoursLeft}h left): Finish P1 + ${state.penaltyRepsRequired} pushups to weld.",
                color = XpRankGold,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBossRepair) {
                    Text("Weld Streak (40 Pushups)")
                }
                if (state.earnedEmergencyShields > 0) {
                    OutlinedButton(onClick = onUseShield) {
                        Text("Use Shield")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onToggleIllness,
            enabled = !data.isIllnessHardLockActive
        ) {
            Text(
                if (data.isIllnessHardLockActive) {
                    "Illness/Travel Active (Reels Locked Today)"
                } else {
                    "Enable Illness/Travel Mode (<10 AM)"
                }
            )
        }
        if (feedbackMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(feedbackMessage, color = XpRankGold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Displays Dopamine Debt pushup balance and Reels Time Bank minutes
@Composable
private fun DopamineBankCard(
    data: DashboardSnapshot,
    onPayPushups: () -> Unit,
    onSimulateBypass: () -> Unit
) {
    val state = data.streakState
    GlassCard(borderAccent = if (state.dopamineDebtPushups > 0) P1CriticalNeon else VerifiedEmerald) {
        Text("Reels Bank & Dopamine Debt", color = CyberCyan, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Reels Bank: ${state.reelsTimeBankMinutes} min",
                color = VerifiedEmerald,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Debt: ${state.dopamineDebtPushups} pushups",
                color = if (state.dopamineDebtPushups > 0) P1CriticalNeon else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPayPushups) {
                Text("+20 Verified Pushups")
            }
            OutlinedButton(
                onClick = onSimulateBypass,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = P1CriticalNeon)
            ) {
                Text("Bypass (-35 RP)")
            }
        }
    }
}

// Displays a single habit card with its RP reward and completion trigger
@Composable
private fun HabitActionCard(
    habit: HabitEntity,
    onComplete: () -> Unit
) {
    GlassCard(borderAccent = if (habit.isCompletedToday) VerifiedEmerald else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(habit.title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    text = "${habit.tier} • +${habit.rpReward} RP",
                    color = if (habit.isCompletedToday) VerifiedEmerald else XpRankGold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Button(
                onClick = onComplete,
                enabled = !habit.isCompletedToday
            ) {
                Text(if (habit.isCompletedToday) "Done" else "Complete")
            }
        }
    }
}

// Displays the most recent RP gain/decay events from Room
@Composable
private fun RecentRpLogCard(data: DashboardSnapshot) {
    GlassCard {
        Text("Recent RP Ledger", color = CyberCyan, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (data.recentEvents.isEmpty()) {
            Text("No RP events logged yet.", color = Color.White.copy(alpha = 0.6f))
        } else {
            data.recentEvents.take(5).forEach { event ->
                val sign = if (event.rpDelta >= 0) "+" else ""
                val color = if (event.rpDelta >= 0) VerifiedEmerald else P1CriticalNeon
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.source, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    Text("$sign${event.rpDelta} RP", color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// Controls daily 5:30 AM kill-proof alarm schedule and immediate test trigger
@Composable
private fun KillProofAlarmCard() {
    val context = LocalContext.current
    val store = remember { DirectBootAlarmStore(context) }
    var alarmEnabled by remember { mutableStateOf(store.isAlarmEnabled()) }
    var alarmHour by remember { mutableStateOf(store.alarmHour()) }
    var alarmMinute by remember { mutableStateOf(store.alarmMinute()) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AlarmScheduler.scheduleDailyAlarm(context, alarmHour, alarmMinute)
            alarmEnabled = true
        }
    }

    val formattedTime = String.format("%02d:%02d", alarmHour, alarmMinute)
    GlassCard(borderAccent = CyberCyan) {
        Text("Kill-Proof Camera Alarm (3x3 Burst)", color = CyberCyan, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (alarmEnabled) "Armed daily at $formattedTime (Direct-Boot ready)" else "Alarm disabled ($formattedTime)",
            color = if (alarmEnabled) VerifiedEmerald else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (alarmEnabled) {
                    AlarmScheduler.cancelDailyAlarm(context)
                    alarmEnabled = false
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        AlarmScheduler.scheduleDailyAlarm(context, alarmHour, alarmMinute)
                        alarmEnabled = true
                    }
                }
            }) {
                Text(if (alarmEnabled) "Disarm Alarm" else "Arm $formattedTime")
            }
            OutlinedButton(onClick = {
                val intent = Intent(context, AlarmForegroundService::class.java).apply {
                    action = AlarmForegroundService.ACTION_START_ALARM
                }
                context.startForegroundService(intent)
            }) {
                Text("Test Alarm Now")
            }
        }
    }
}
