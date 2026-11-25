package com.bhupendra.disciplineengine.blocker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.camera.CameraVerifyActivity
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.DisciplineRepository
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Full-screen glassmorphic shield shown when Reels/Shorts, unsafe URLs, or Settings tamper is blocked
class BlockerShieldActivity : ComponentActivity() {

    // Renders block details, active P1 focus task, and ethical exercise-to-unlock options
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: BlockReason.REELS_SHORTS_HARD_LOCK.name
        val p1Task = intent.getStringExtra(EXTRA_P1_TASK) ?: "P1 Core Deep Study Block"
        val blockedCount = intent.getIntExtra(EXTRA_BLOCKED_COUNT, 1)
        val dopamineDebt = intent.getIntExtra(EXTRA_DOPAMINE_DEBT, 0)
        val allowPushupUnlock = intent.getBooleanExtra(EXTRA_ALLOW_PUSHUP_UNLOCK, false)

        setContent {
            DisciplineTheme {
                val scope = rememberCoroutineScope()
                var cooldownActive by remember { mutableStateOf(false) }
                var cooldownSeconds by remember { mutableIntStateOf(1800) } // 30-min impulse timer

                LaunchedEffect(cooldownActive) {
                    while (cooldownActive && cooldownSeconds > 0) {
                        delay(1000L)
                        cooldownSeconds -= 1
                    }
                    if (cooldownActive && cooldownSeconds == 0) {
                        BlockerStateStore(this@BlockerShieldActivity).grantEthicalSettingsUnlock()
                        finish()
                    }
                }

                val unlockCameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val reps = result.data?.getIntExtra(CameraVerifyActivity.EXTRA_VERIFIED_REPS, 20) ?: 20
                        scope.launch {
                            if (reason == BlockReason.SETTINGS_ANTI_TAMPER.name) {
                                BlockerStateStore(this@BlockerShieldActivity).grantEthicalSettingsUnlock()
                            } else {
                                DisciplineRepository(DisciplineDatabase.get(DisciplineApp.appContext).dao())
                                    .recordVerifiedPushups(reps)
                            }
                            finish()
                        }
                    }
                }

                Surface(Modifier.fillMaxSize(), color = BackgroundCanvas) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassCard(borderAccent = P1CriticalNeon) {
                            Text(
                                text = "REEL SHIELD INTERCEPTION",
                                color = P1CriticalNeon,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Trigger: $reason", color = XpRankGold, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Text("Unfinished P1 Mission:", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                            Text(p1Task, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("Blocked temptations today: $blockedCount", color = VerifiedEmerald)
                            Text("Dopamine Debt: $dopamineDebt pushups", color = P1CriticalNeon)

                            Spacer(Modifier.height(16.dp))

                            if (reason == BlockReason.PRIVATE_DNS_DISABLED_BROWSER_LOCK.name) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    }
                                ) {
                                    Text("Set Private DNS: family.cloudflare-dns.com")
                                }
                            } else if (reason == BlockReason.SETTINGS_ANTI_TAMPER.name) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        unlockCameraLauncher.launch(
                                            Intent(this@BlockerShieldActivity, CameraVerifyActivity::class.java).apply {
                                                putExtra(CameraVerifyActivity.EXTRA_TARGET_REPS, 50)
                                            }
                                        )
                                    }
                                ) {
                                    Text("Ethical Unlock: 50 Camera Pushups")
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { cooldownActive = true }
                                ) {
                                    val mins = cooldownSeconds / 60
                                    val secs = cooldownSeconds % 60
                                    Text(
                                        if (cooldownActive) {
                                            "Cooldown active: %02d:%02d (resets if closed)".format(mins, secs)
                                        } else {
                                            "Start 30-Min Impulse Cooldown"
                                        }
                                    )
                                }
                            } else if (allowPushupUnlock) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        unlockCameraLauncher.launch(
                                            Intent(this@BlockerShieldActivity, CameraVerifyActivity::class.java).apply {
                                                putExtra(CameraVerifyActivity.EXTRA_TARGET_REPS, 20)
                                            }
                                        )
                                    }
                                ) {
                                    Text("Do 20 Camera Pushups -> Unlock 10 Mins")
                                }
                            } else {
                                Text(
                                    text = "STUDY / ILLNESS HARD LOCK ACTIVE — Zero exceptions.",
                                    color = P1CriticalNeon,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { finish() }
                            ) {
                                Text("Return to Mission")
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_P1_TASK = "extra_p1_task"
        const val EXTRA_BLOCKED_COUNT = "extra_blocked_count"
        const val EXTRA_DOPAMINE_DEBT = "extra_dopamine_debt"
        const val EXTRA_ALLOW_PUSHUP_UNLOCK = "extra_allow_pushup_unlock"
    }
}
