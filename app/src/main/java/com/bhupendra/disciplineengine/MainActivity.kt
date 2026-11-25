package com.bhupendra.disciplineengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.RpEventEntity
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme
import com.bhupendra.disciplineengine.ui.theme.GlassBorderEnd
import com.bhupendra.disciplineengine.ui.theme.GlassBorderTop
import com.bhupendra.disciplineengine.ui.theme.GlassSurface
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Hosts the root Compose UI shell
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisciplineTheme {
                Surface(Modifier.fillMaxSize(), color = BackgroundCanvas) {
                    DashboardScreen()
                }
            }
        }
    }
}

// Displays the Phase 1 Rank Banner and RP test trigger
@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val dao = remember { DisciplineDatabase.get(DisciplineApp.appContext).dao() }
    var totalRp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        totalRp = dao.totalRp()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard {
            Text(
                text = "DISCIPLINE ENGINE",
                color = CyberCyan,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Total RP: $totalRp",
                color = XpRankGold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text("Rank: BRONZE", color = VerifiedEmerald)
        }

        Spacer(Modifier.height(16.dp))

        GlassCard {
            Text("Complete P1 Study Block (+40 RP)", color = P1CriticalNeon)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    dao.addRpEvent(
                        RpEventEntity(
                            timestamp = System.currentTimeMillis(),
                            source = "P1_BLOCK",
                            rpDelta = 40
                        )
                    )
                    totalRp = dao.totalRp()
                }
            }) {
                Text("Claim RP (Test)")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Phase 1 Ready | Alarm: Phase 3 | Blocker: Phase 6",
            color = VerifiedEmerald,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// Reusable dark glassmorphic card with subtle neon border
@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(GlassBorderTop, GlassBorderEnd))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
