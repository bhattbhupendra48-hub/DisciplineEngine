package com.bhupendra.disciplineengine.alarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

// Full-screen lockscreen alarm UI that blocks back navigation until exercise or bypass
class AlarmVerifyActivity : ComponentActivity() {

    // Configures lockscreen flags, disables back button, and renders the burst cycle alarm UI
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                notifyInteraction()
            }
        })

        val store = DirectBootAlarmStore(this)
        val requiredPushups = store.requiredPushups()

        setContent {
            DisciplineTheme {
                val burst by AlarmForegroundService.burstState.collectAsState()
                val scope = rememberCoroutineScope()
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val verifiedReps = result.data?.getIntExtra(
                            CameraVerifyActivity.EXTRA_VERIFIED_REPS,
                            requiredPushups
                        ) ?: requiredPushups
                        scope.launch {
                            val repo = DisciplineRepository(
                                DisciplineDatabase.get(DisciplineApp.appContext).dao()
                            )
                            repo.completeHabit("habit_pushup_alarm")
                            repo.recordVerifiedPushups(verifiedReps)
                            dismissAlarmService()
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
                                text = "KILL-PROOF WAKEUP ALARM",
                                color = P1CriticalNeon,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            val cycleText = burst?.let {
                                "Cycle ${it.cycleNumber}/${it.totalCycles} • ${it.phase.name} (${it.phaseRemainingSeconds}s)"
                            } ?: "Cycle 1/3 • RINGING"
                            Text(cycleText, color = XpRankGold)

                            Spacer(Modifier.height(6.dp))
                            val motionText = if (burst?.motionOrTouchDetected == true) {
                                "Physical presence detected"
                            } else {
                                "Waiting for motion/touch (15 min timeout = -35 RP)"
                            }
                            Text(motionText, color = CyberCyan, style = MaterialTheme.typography.bodySmall)

                            Spacer(Modifier.height(16.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    notifyInteraction()
                                    val camIntent = Intent(this@AlarmVerifyActivity, CameraVerifyActivity::class.java).apply {
                                        putExtra(CameraVerifyActivity.EXTRA_TARGET_REPS, requiredPushups)
                                        putExtra(CameraVerifyActivity.EXTRA_STRICT_MODE, true)
                                    }
                                    cameraLauncher.launch(camIntent)
                                }
                            ) {
                                Text("Start Camera Pushups ($requiredPushups reps -> +25 RP)")
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = P1CriticalNeon),
                                onClick = {
                                    notifyInteraction()
                                    scope.launch {
                                        val repo = DisciplineRepository(
                                            DisciplineDatabase.get(DisciplineApp.appContext).dao()
                                        )
                                        repo.triggerEmergencyBypass()
                                        dismissAlarmService()
                                    }
                                }
                            ) {
                                Text("Emergency Bypass (-35 RP / +30 Debt)")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "BlazePose-Lite + 4s Watchdog + <5 Lux Torch Ready",
                            color = VerifiedEmerald.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    // Registers physical touch interaction on the foreground alarm service
    private fun notifyInteraction() {
        startService(
            Intent(this, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_REGISTER_TOUCH
            }
        )
    }

    // Stops the foreground alarm service and closes the lockscreen activity
    private fun dismissAlarmService() {
        startService(
            Intent(this, AlarmForegroundService::class.java).apply {
                action = AlarmForegroundService.ACTION_DISMISS_VERIFIED
            }
        )
        finish()
    }
}
