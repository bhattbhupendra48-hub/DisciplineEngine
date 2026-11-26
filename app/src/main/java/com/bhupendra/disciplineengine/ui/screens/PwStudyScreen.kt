package com.bhupendra.disciplineengine.ui.screens

import android.content.Intent
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.PwDashboardSnapshot
import com.bhupendra.disciplineengine.data.PwNoticeTestEntity
import com.bhupendra.disciplineengine.data.PwRepository
import com.bhupendra.disciplineengine.pw.ChapterTriageItem
import com.bhupendra.disciplineengine.pw.PwItemType
import com.bhupendra.disciplineengine.pw.TriageStrategy
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch
import java.time.LocalDate

// Renders the Phase 7 Zero-Open PW Notice Board, Exam Triage Engine, and Missed Test Vault
@Composable
fun PwStudyScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember {
        PwRepository(
            dao = DisciplineDatabase.get(DisciplineApp.appContext).dao(),
            appContext = DisciplineApp.appContext
        )
    }

    var snapshot by remember { mutableStateOf<PwDashboardSnapshot?>(null) }
    var showCsvImport by remember { mutableStateOf(false) }
    var csvInput by remember { mutableStateOf("Physics,Electrostatics,8,0,TIER1_HARD_FULL_BATCH") }
    var launchFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        snapshot = repository.loadPwDashboard()
    }

    val data = snapshot ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(borderAccent = CyberCyan) {
                Text(
                    text = "ZERO-OPEN PW STUDY AI",
                    color = CyberCyan,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Offline planner • syllabus data stays on this device",
                    color = VerifiedEmerald,
                    style = MaterialTheme.typography.bodySmall
                )
                if (data.batchClockAddedLectures > 0) {
                    Text(
                        text = "Offline Batch Clock logged +${data.batchClockAddedLectures} scheduled lecture(s).",
                        color = XpRankGold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val pwIntent = context.packageManager.getLaunchIntentForPackage(
                            "xyz.penpencil.physicswallah"
                        )
                        if (pwIntent != null) {
                            context.startActivity(pwIntent)
                        } else {
                            launchFeedback = "Physics Wallah app not installed on device."
                        }
                    }) {
                        Text("1-Tap Launch PW")
                    }
                }
                if (launchFeedback != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(launchFeedback!!, color = XpRankGold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            val plan = data.triagePlan
            GlassCard(borderAccent = if (plan.needsTriageCompression) XpRankGold else VerifiedEmerald) {
                Text("Exam Syllabus Triage Engine", color = XpRankGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Exam in ${plan.daysRemaining}d • Study Budget: ${data.dailySelfStudyHours}h/day (${plan.availableSelfStudyMinutes / 60}h total)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Full Batch Needs: ${plan.fullBatchRequiredMinutes / 60}h -> Triaged Plan: ${plan.triagedRequiredMinutes / 60}h",
                    color = if (plan.needsTriageCompression) P1CriticalNeon else VerifiedEmerald,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { snapshot = repository.applyPreset("NEET 2.0") } }) { Text("NEET 2.0") }
                    OutlinedButton(onClick = { scope.launch { snapshot = repository.applyPreset("JEE 2.0") } }) { Text("JEE 2.0") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            snapshot = repository.updateExamTarget(data.examDaysAhead - 7, data.dailySelfStudyHours)
                        }
                    }) { Text("-7d Exam") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            snapshot = repository.updateExamTarget(data.examDaysAhead + 7, data.dailySelfStudyHours)
                        }
                    }) { Text("+7d Exam") }
                    OutlinedButton(onClick = { showCsvImport = !showCsvImport }) {
                        Text("Paste / CSV")
                    }
                }
                if (showCsvImport) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = csvInput,
                        onValueChange = { csvInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Subject,Chapter,Total,Done,Tier") }
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        scope.launch {
                            snapshot = repository.importBatchCsv(csvInput)
                            showCsvImport = false
                        }
                    }) { Text("Import Text / CSV") }
                }
            }
        }

        item {
            GlassCard(borderAccent = CyberCyan) {
                Text("Day-by-Day Exam Plan", color = CyberCyan, fontWeight = FontWeight.Bold)
                Text("Full Batch, YouTube One-Shot, or Skip / Notes & PYQ. Check off completed study for +30 RP.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }

        items(data.studyTasks, key = { it.taskId }) { task ->
            val uriHandler = LocalUriHandler.current
            GlassCard(borderAccent = if (task.isCompleted) VerifiedEmerald else CyberCyan) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { checked ->
                        if (checked) scope.launch { snapshot = repository.completeStudyTask(task.taskId) }
                    })
                    Column(Modifier.weight(1f)) {
                        Text("${LocalDate.ofEpochDay(task.dateEpochDay)} • ${task.title}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${task.estimatedMinutes} min", color = XpRankGold, style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.strategy == "ONESHOT_2H_PRACTICE") OutlinedButton(onClick = { uriHandler.openUri(task.youtubeSearchUrl) }) { Text("YouTube") }
                }
            }
        }

        items(data.triagePlan.items, key = { it.chapter.chapterId }) { item ->
            TriageChapterCard(
                item = item,
                onCompleteLecture = {
                    scope.launch { snapshot = repository.markChapterProgress(item.chapter.chapterId, false) }
                },
                onCompleteDpp = {
                    scope.launch { snapshot = repository.markChapterProgress(item.chapter.chapterId, true) }
                }
            )
        }

        item {
            Text("Live PW Notices & Missed Test Vault", color = CyberCyan, fontWeight = FontWeight.Bold)
        }

        items(data.noticesAndTests, key = { it.itemId }) { item ->
            NoticeOrTestCard(item = item) {
                scope.launch { snapshot = repository.completeTest(item.itemId) }
            }
        }
    }
}

// Displays a single triaged PW chapter with strategy recommendation and quick progress actions
@Composable
private fun TriageChapterCard(
    item: ChapterTriageItem,
    onCompleteLecture: () -> Unit,
    onCompleteDpp: () -> Unit
) {
    val ch = item.chapter
    val tierColor = when (item.strategy) {
        TriageStrategy.FULL_BATCH_1_5X -> P1CriticalNeon
        TriageStrategy.ONESHOT_2H_PRACTICE -> XpRankGold
        TriageStrategy.NOTES_AND_PYQ -> VerifiedEmerald
        TriageStrategy.FULL_BATCH_1_0X -> CyberCyan
    }

    GlassCard(borderAccent = tierColor) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${ch.subjectName}: ${ch.chapterName}", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${ch.completedLectures}/${ch.totalBatchLectures} lec", color = CyberCyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${item.strategy.label} (~${item.estimatedMinutes}m)",
            color = tierColor,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (ch.dppCompleted) "DPP Completed" else "DPP Pending",
            color = if (ch.dppCompleted) VerifiedEmerald else Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCompleteLecture) { Text("+1 Lecture (+30 RP)") }
            if (!ch.dppCompleted) {
                OutlinedButton(onClick = onCompleteDpp) { Text("Finish DPP") }
            }
        }
    }
}

// Displays a caught PW notification or Missed Test Vault unlock card
@Composable
private fun NoticeOrTestCard(
    item: PwNoticeTestEntity,
    onCompleteTest: () -> Unit
) {
    val isTest = item.itemType == PwItemType.TEST_SCHEDULED.name
    val accent = when {
        isTest && !item.isTestUnlocked -> P1CriticalNeon
        isTest && item.isTestCompleted -> VerifiedEmerald
        else -> CyberCyan
    }

    GlassCard(borderAccent = accent) {
        Text(item.itemType, color = accent, style = MaterialTheme.typography.labelSmall)
        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
        if (item.rawNotificationText.isNotBlank()) {
            Text(item.rawNotificationText, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
        if (isTest) {
            val slotInfo = item.scheduledSundaySlotDate?.let { LocalDate.ofEpochDay(it).toString() } ?: "Finish syllabus to unlock"
            Text(
                text = if (item.isTestUnlocked) "Unlocked • Sunday Slot: $slotInfo" else "LOCKED IN TEST VAULT • $slotInfo",
                color = if (item.isTestUnlocked) VerifiedEmerald else P1CriticalNeon,
                style = MaterialTheme.typography.labelSmall
            )
            if (item.isTestUnlocked && !item.isTestCompleted) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = onCompleteTest) { Text("Mark Test Done (+30 RP)") }
            }
        }
    }
}
