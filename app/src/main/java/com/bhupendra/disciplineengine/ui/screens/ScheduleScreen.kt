package com.bhupendra.disciplineengine.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.ScheduleBlockEntity
import com.bhupendra.disciplineengine.data.ScheduleProfileEntity
import com.bhupendra.disciplineengine.data.ScheduleRepository
import com.bhupendra.disciplineengine.data.ScheduleSnapshot
import com.bhupendra.disciplineengine.domain.ScheduleEngine
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch

// Renders the Phase 4 7-Question Schedule Creator & 8A+8C Live Timeline
@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val repository = remember {
        ScheduleRepository(DisciplineDatabase.get(DisciplineApp.appContext).dao())
    }
    var snapshot by remember { mutableStateOf<ScheduleSnapshot?>(null) }
    var showWizard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshot = repository.loadTodaySchedule()
    }

    val data = snapshot ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(borderAccent = CyberCyan) {
                Text(
                    text = "8A + 8C SCHEDULE ENGINE",
                    color = CyberCyan,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Danger Lock: ${ScheduleEngine.formatMinutesAsTime(data.profile.dangerStartMinutes)} - ${ScheduleEngine.formatMinutesAsTime(data.profile.dangerEndMinutes)}",
                    color = XpRankGold,
                    style = MaterialTheme.typography.bodySmall
                )
                if (data.statusBanner != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(data.statusBanner, color = VerifiedEmerald, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch { snapshot = repository.runAutoReschedule() }
                    }) {
                        Text("Run 8A+8C Triage")
                    }
                    OutlinedButton(onClick = { showWizard = !showWizard }) {
                        Text(if (showWizard) "Hide 7 Questions" else "Edit 7 Questions")
                    }
                }
            }
        }

        if (showWizard) {
            item {
                SevenQuestionsCard(
                    initialProfile = data.profile,
                    onGenerate = { updated ->
                        scope.launch {
                            snapshot = repository.saveProfileAndRegenerate(updated)
                            showWizard = false
                        }
                    }
                )
            }
        }

        items(data.todayBlocks, key = { it.blockId }) { block ->
            ScheduleBlockCard(
                block = block,
                onComplete = {
                    scope.launch { snapshot = repository.updateBlockStatus(block.blockId, "COMPLETED") }
                },
                onMissed = {
                    scope.launch { snapshot = repository.updateBlockStatus(block.blockId, "MISSED") }
                }
            )
        }
    }
}

// Interactive 7-question onboarding editor to tune daily anchors and energy windows
@Composable
private fun SevenQuestionsCard(
    initialProfile: ScheduleProfileEntity,
    onGenerate: (ScheduleProfileEntity) -> Unit
) {
    var profile by remember { mutableStateOf(initialProfile) }

    GlassCard(borderAccent = XpRankGold) {
        Text("7-Question Schedule Onboarding", color = XpRankGold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        TimeAdjustRow("Q1 Wake Anchor", profile.wakeUpMinutes) {
            profile = profile.copy(wakeUpMinutes = (profile.wakeUpMinutes + it).coerceIn(240, 540))
        }
        TimeAdjustRow("Q1 Sleep Anchor", profile.sleepMinutes) {
            profile = profile.copy(sleepMinutes = (profile.sleepMinutes + it).coerceIn(1260, 1435))
        }
        TimeAdjustRow("Q2 Coaching Start", profile.coachingStartMinutes) {
            profile = profile.copy(coachingStartMinutes = (profile.coachingStartMinutes + it).coerceIn(360, 960))
        }
        TimeAdjustRow("Q3 Sports Window", profile.sportsStartMinutes) {
            val newStart = (profile.sportsStartMinutes + it).coerceIn(900, 1260)
            profile = profile.copy(sportsStartMinutes = newStart, sportsEndMinutes = newStart + 60)
        }
        TimeAdjustRow("Q5 Peak Focus Start", profile.peakFocusStartMinutes) {
            profile = profile.copy(peakFocusStartMinutes = (profile.peakFocusStartMinutes + it).coerceIn(300, 1200))
        }
        TimeAdjustRow("Q6 P1 Study (mins)", profile.p1TargetMinutes, isDuration = true) {
            profile = profile.copy(p1TargetMinutes = (profile.p1TargetMinutes + it).coerceIn(60, 240))
        }
        TimeAdjustRow("Q7 Danger Lock Start", profile.dangerStartMinutes) {
            val start = (profile.dangerStartMinutes + it).coerceIn(1080, 1380)
            profile = profile.copy(dangerStartMinutes = start, dangerEndMinutes = (start + 90).coerceAtMost(1435))
        }

        Spacer(Modifier.height(10.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = { onGenerate(profile) }) {
            Text("Generate 8A+8C Energy Schedule")
        }
    }
}

// Stepper row for adjusting time anchors or block durations in 30-minute increments
@Composable
private fun TimeAdjustRow(
    label: String,
    valueMinutes: Int,
    isDuration: Boolean = false,
    onDelta: (Int) -> Unit
) {
    val display = if (isDuration) "${valueMinutes}m" else ScheduleEngine.formatMinutesAsTime(valueMinutes)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$label: $display", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onDelta(-30) }) { Text("-30m") }
            OutlinedButton(onClick = { onDelta(30) }) { Text("+30m") }
        }
    }
}

// Displays a single schedule timeline card with status and quick triage controls
@Composable
private fun ScheduleBlockCard(
    block: ScheduleBlockEntity,
    onComplete: () -> Unit,
    onMissed: () -> Unit
) {
    val accent = when {
        block.status == "COMPLETED" -> VerifiedEmerald
        block.status == "MISSED" -> P1CriticalNeon
        block.blockType == "SHOCK_ABSORBER_BUFFER_8A" -> XpRankGold
        block.priorityTier == "P1_CORE_NON_NEGOTIABLE" -> P1CriticalNeon
        else -> CyberCyan
    }

    val timeLabel = "${ScheduleEngine.formatMinutesAsTime(block.startTimeMinutesFromMidnight)} (${block.durationMinutes}m)"
    GlassCard(borderAccent = accent) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel, color = accent, style = MaterialTheme.typography.labelMedium)
            Text(block.status, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(block.title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("${block.priorityTier} • ${block.energyTag}", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)

        if (block.blockType != "TRANSITION_BREAK" && block.status !in listOf("COMPLETED", "ROLLED_OVER")) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onComplete) { Text("Done") }
                if (block.priorityTier != "ANCHOR_IMMUTABLE") {
                    OutlinedButton(
                        onClick = onMissed,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = P1CriticalNeon)
                    ) { Text("Missed") }
                }
            }
        }
    }
}
