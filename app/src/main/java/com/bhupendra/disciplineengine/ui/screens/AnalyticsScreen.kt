package com.bhupendra.disciplineengine.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.data.AnalyticsRecorder
import com.bhupendra.disciplineengine.data.DailyAnalyticsEntity
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.StreakEntity
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Renders Phase 9 native Jetpack Compose Canvas graphs with zero third-party chart bloat
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    var streak by remember { mutableStateOf(StreakEntity()) }
    var logs by remember { mutableStateOf<List<DailyAnalyticsEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        val dao = DisciplineDatabase.get(DisciplineApp.appContext).dao()
        streak = dao.getStreak() ?: StreakEntity()
        logs = dao.getRecentAnalytics(365)
    }

    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    val todayLog = logs.firstOrNull { it.dateEpochDay == todayEpoch }
    val urgeHours = AnalyticsRecorder.parseHourlyArray(todayLog?.blockedReelsAttemptsByHourJson)
    val studyHours = AnalyticsRecorder.parseHourlyArray(todayLog?.deepStudyMinutesByHourJson)

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RpgRadarCard(streak = streak)
        }
        item {
            Heatmap365Card(logs = logs, todayEpochDay = todayEpoch)
        }
        item {
            UrgeVsOutputCard(urgeHours = urgeHours, studyHours = studyHours)
        }
        item {
            KeystoneConsistencyBarCard(logs = logs, todayEpochDay = todayEpoch)
        }
    }
}

// Draws the 5-Axis RPG Character Radar Chart (STR, INT, WIL, SPD, CON)
@Composable
fun RpgRadarCard(streak: StreakEntity) {
    val stats = listOf(
        "STR" to streak.statSTR,
        "INT" to streak.statINT,
        "WIL" to streak.statWIL,
        "SPD" to streak.statSPD,
        "CON" to streak.statCON
    )

    GlassCard(borderAccent = CyberCyan) {
        Text("5-AXIS RPG CHARACTER RADAR", color = CyberCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.forEach { (label, value) ->
                Text("$label ${value.toInt()}", color = XpRankGold, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(210.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) * 0.42f
                val count = 5
                val startAngle = -PI.toFloat() / 2f

                // Concentric web rings
                for (ring in 1..4) {
                    val r = radius * (ring / 4f)
                    val ringPath = Path()
                    for (i in 0 until count) {
                        val angle = startAngle + i * (2f * PI.toFloat() / count)
                        val pt = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
                        if (i == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
                    }
                    ringPath.close()
                    drawPath(ringPath, color = Color.White.copy(alpha = 0.14f), style = Stroke(1.5f))
                }

                // Spokes
                for (i in 0 until count) {
                    val angle = startAngle + i * (2f * PI.toFloat() / count)
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = center,
                        end = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle)),
                        strokeWidth = 1.5f
                    )
                }

                // Player stat polygon
                val statPath = Path()
                stats.forEachIndexed { idx, (_, raw) ->
                    val frac = (raw / 100f).coerceIn(0.08f, 1f)
                    val angle = startAngle + idx * (2f * PI.toFloat() / count)
                    val pt = Offset(center.x + radius * frac * cos(angle), center.y + radius * sin(angle) * frac)
                    if (idx == 0) statPath.moveTo(pt.x, pt.y) else statPath.lineTo(pt.x, pt.y)
                }
                statPath.close()
                drawPath(statPath, color = CyberCyan.copy(alpha = 0.28f))
                drawPath(statPath, color = CyberCyan, style = Stroke(3f))
            }
        }
    }
}

// Draws a 53-week x 7-day native Canvas heatmap across 365 days
@Composable
fun Heatmap365Card(logs: List<DailyAnalyticsEntity>, todayEpochDay: Long) {
    val logMap = remember(logs) { logs.associateBy { it.dateEpochDay } }

    GlassCard(borderAccent = VerifiedEmerald) {
        Text("365-DAY CONSISTENCY HEATMAP", color = VerifiedEmerald, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Dark = 0% • Green = Verified • Gold = 100% Perfect Day",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
            val cols = 53
            val rows = 7
            val gap = 2.dp.toPx()
            val cellSize = ((size.width - gap * (cols - 1)) / cols).coerceAtMost((size.height - gap * (rows - 1)) / rows)
            val startEpoch = todayEpochDay - 364L

            for (dayOffset in 0..364) {
                val col = dayOffset / 7
                val row = dayOffset % 7
                val pct = logMap[startEpoch + dayOffset]?.habitsCompletedPercent ?: 0f
                val cellColor = when {
                    pct >= 99f -> XpRankGold
                    pct >= 70f -> VerifiedEmerald
                    pct >= 40f -> Color(0xFF059669)
                    pct > 0f -> Color(0xFF064E3B)
                    else -> Color(0xFF1E293B)
                }
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(col * (cellSize + gap), row * (cellSize + gap)),
                    size = Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

// Draws hourly Urge-vs-Output curves comparing blocked temptations vs deep study minutes
@Composable
fun UrgeVsOutputCard(urgeHours: IntArray, studyHours: IntArray) {
    GlassCard(borderAccent = P1CriticalNeon) {
        Text("URGE VS. OUTPUT (24H CURVE)", color = P1CriticalNeon, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Red = Blocked Temptations", color = P1CriticalNeon, style = MaterialTheme.typography.labelSmall)
            Text("Green = Deep Study Output", color = VerifiedEmerald, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val maxUrge = (urgeHours.maxOrNull() ?: 1).coerceAtLeast(3).toFloat()
            val maxStudy = (studyHours.maxOrNull() ?: 1).coerceAtLeast(60).toFloat()
            val stepX = size.width / 23f

            val urgePath = Path()
            val studyPath = Path()
            for (h in 0 until 24) {
                val x = h * stepX
                val urgeY = size.height - (urgeHours[h] / maxUrge).coerceIn(0f, 1f) * (size.height - 8f)
                val studyY = size.height - (studyHours[h] / maxStudy).coerceIn(0f, 1f) * (size.height - 8f)
                if (h == 0) {
                    urgePath.moveTo(x, urgeY)
                    studyPath.moveTo(x, studyY)
                } else {
                    urgePath.lineTo(x, urgeY)
                    studyPath.lineTo(x, studyY)
                }
            }
            drawPath(studyPath, color = VerifiedEmerald, style = Stroke(3.5f))
            drawPath(urgePath, color = P1CriticalNeon, style = Stroke(3.5f))
        }
    }
}

// Draws 14-day Keystone Habit consistency bars using native Canvas
@Composable
fun KeystoneConsistencyBarCard(logs: List<DailyAnalyticsEntity>, todayEpochDay: Long) {
    val logMap = remember(logs) { logs.associateBy { it.dateEpochDay } }

    GlassCard(borderAccent = XpRankGold) {
        Text("14-DAY KEYSTONE CONSISTENCY", color = XpRankGold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val days = 14
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (days - 1)) / days

            for (i in 0 until days) {
                val epoch = todayEpochDay - (days - 1 - i)
                val pct = (logMap[epoch]?.habitsCompletedPercent ?: 0f).coerceIn(0f, 100f)
                val barHeight = (pct / 100f) * size.height
                val barColor = when {
                    pct >= 80f -> VerifiedEmerald
                    pct >= 50f -> XpRankGold
                    pct > 0f -> P1CriticalNeon
                    else -> Color(0xFF1E293B)
                }
                val renderedHeight = barHeight.coerceAtLeast(8.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(i * (barWidth + gap), size.height - renderedHeight),
                    size = Size(barWidth, renderedHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}
