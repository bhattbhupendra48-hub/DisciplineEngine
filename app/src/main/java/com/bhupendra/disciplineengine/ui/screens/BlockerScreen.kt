package com.bhupendra.disciplineengine.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.bhupendra.disciplineengine.blocker.BlockerStateStore
import com.bhupendra.disciplineengine.blocker.DisciplineAccessibilityService
import com.bhupendra.disciplineengine.blocker.PrivateDnsGuard
import com.bhupendra.disciplineengine.camera.CameraVerifyActivity
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.DisciplineRepository
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.launch

// Renders the Phase 6 Reel Shield, Private DNS verifier, and Regain-style Settings Guard panel
@Composable
fun BlockerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockerStore = remember { BlockerStateStore(context) }

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var privateDnsOk by remember { mutableStateOf(false) }
    var privateDnsText by remember { mutableStateOf("checking...") }
    var blockedToday by remember { mutableIntStateOf(0) }
    var reelsBank by remember { mutableIntStateOf(0) }
    var dopamineDebt by remember { mutableIntStateOf(0) }

    // Refreshes service, DNS, and bank status whenever user enters Reel Shield tab
    fun refreshStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        privateDnsOk = PrivateDnsGuard.isPrivateDnsEnforced(context)
        privateDnsText = PrivateDnsGuard.currentSpecifier(context)
        blockedToday = blockerStore.getBlockedToday()
        scope.launch {
            val streak = DisciplineDatabase.get(DisciplineApp.appContext).dao().getStreak()
            reelsBank = streak?.reelsTimeBankMinutes ?: 0
            dopamineDebt = streak?.dopamineDebtPushups ?: 0
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val reps = result.data?.getIntExtra(CameraVerifyActivity.EXTRA_VERIFIED_REPS, 20) ?: 20
            scope.launch {
                DisciplineRepository(DisciplineDatabase.get(DisciplineApp.appContext).dao())
                    .recordVerifiedPushups(reps)
                refreshStatus()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(borderAccent = if (accessibilityEnabled && privateDnsOk) VerifiedEmerald else P1CriticalNeon) {
                Text("ZERO-VPN REEL & 18+ SHIELD", color = CyberCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Accessibility Guard: ${if (accessibilityEnabled) "ACTIVE (0% idle CPU)" else "DISABLED"}",
                    color = if (accessibilityEnabled) VerifiedEmerald else P1CriticalNeon
                )
                Text(
                    text = "OS Private DNS: $privateDnsText",
                    color = if (privateDnsOk) VerifiedEmerald else XpRankGold
                )
                Text("Blocked Temptations Today: $blockedToday", color = Color.White)
                Text("Reels Bank: ${reelsBank}m | Debt: $dopamineDebt pushups", color = CyberCyan)
                Spacer(Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                ) {
                    Text(if (accessibilityEnabled) "Manage Accessibility Service" else "Enable Accessibility Shield")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                ) {
                    Text("Set Private DNS (family.cloudflare-dns.com)")
                }
            }
        }

        item {
            GlassCard(borderAccent = XpRankGold) {
                Text("Exercise-to-Unlock & Ethical Settings Gate", color = XpRankGold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "20 verified pushups clear Debt first, then unlock 10 mins of free-time scrolling. 50 pushups unlock Settings for 5 mins.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        cameraLauncher.launch(
                            Intent(context, CameraVerifyActivity::class.java).apply {
                                putExtra(CameraVerifyActivity.EXTRA_TARGET_REPS, 20)
                            }
                        )
                    }
                ) {
                    Text("Earn +10m Reels / Clear Debt (20 Pushups)")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { refreshStatus() }
                ) {
                    Text("Refresh Guard Status")
                }
            }
        }
    }
}

// Checks Android secure settings to verify DisciplineAccessibilityService is enabled
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${DisciplineAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}
