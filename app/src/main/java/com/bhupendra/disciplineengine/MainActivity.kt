package com.bhupendra.disciplineengine

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.ui.components.AppleMorphDock
import com.bhupendra.disciplineengine.ui.screens.AnalyticsScreen
import com.bhupendra.disciplineengine.ui.screens.BlockerScreen
import com.bhupendra.disciplineengine.ui.screens.DashboardScreen
import com.bhupendra.disciplineengine.ui.screens.PushupArenaScreen
import com.bhupendra.disciplineengine.ui.screens.PwStudyScreen
import com.bhupendra.disciplineengine.ui.screens.ScheduleScreen
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme

class MainActivity : ComponentActivity() {
    // Hosts the dual-orientation shell with floating Gaussian Apple Morphing Dock
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisciplineTheme {
                var selectedModule by rememberSaveable { mutableIntStateOf(0) }
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                Surface(Modifier.fillMaxSize(), color = BackgroundCanvas) {
                    Box(Modifier.fillMaxSize()) {
                        if (isLandscape) {
                            // Landscape 45% Left (Habits/Schedule) / 55% Right (Graphs/PW/Arena/Shield)
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 96.dp)
                            ) {
                                Box(Modifier.weight(0.45f).fillMaxHeight()) {
                                    when (selectedModule) {
                                        1 -> ScheduleScreen()
                                        else -> DashboardScreen(allowInternalSplit = false)
                                    }
                                }
                                Box(Modifier.weight(0.55f).fillMaxHeight()) {
                                    when (selectedModule) {
                                        2 -> PwStudyScreen()
                                        3 -> PushupArenaScreen()
                                        4 -> BlockerScreen()
                                        else -> AnalyticsScreen()
                                    }
                                }
                            }
                        } else {
                            // Portrait single-column with spring morph transitions
                            AnimatedContent(
                                targetState = selectedModule,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 96.dp),
                                transitionSpec = {
                                    (fadeIn(spring(dampingRatio = 0.75f, stiffness = 380f)) +
                                        scaleIn(
                                            initialScale = 0.94f,
                                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)
                                        )) togetherWith fadeOut()
                                },
                                label = "module_morph"
                            ) { idx ->
                                when (idx) {
                                    0 -> DashboardScreen(allowInternalSplit = false)
                                    1 -> ScheduleScreen()
                                    2 -> PwStudyScreen()
                                    3 -> PushupArenaScreen()
                                    4 -> BlockerScreen()
                                    else -> AnalyticsScreen()
                                }
                            }
                        }

                        AppleMorphDock(
                            selectedIndex = selectedModule,
                            onSelectModule = { selectedModule = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(
                                    if (isLandscape) Modifier.fillMaxWidth(0.60f) else Modifier.fillMaxWidth()
                                )
                        )
                    }
                }
            }
        }
    }
}
