package com.bhupendra.disciplineengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bhupendra.disciplineengine.ui.screens.BlockerScreen
import com.bhupendra.disciplineengine.ui.screens.DashboardScreen
import com.bhupendra.disciplineengine.ui.screens.PwStudyScreen
import com.bhupendra.disciplineengine.ui.screens.ScheduleScreen
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme
import com.bhupendra.disciplineengine.ui.theme.GlassSurface

class MainActivity : ComponentActivity() {
    // Hosts the root Compose UI shell with Dark Glassmorphism theme
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DisciplineTheme {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    containerColor = BackgroundCanvas,
                    bottomBar = {
                        NavigationBar(containerColor = GlassSurface) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Text("HQ") },
                                label = { Text("HQ & Habits") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Text("8A") },
                                label = { Text("Schedule") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Text("PW") },
                                label = { Text("PW Study") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Text("SH") },
                                label = { Text("Reel Shield") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> DashboardScreen()
                            1 -> ScheduleScreen()
                            2 -> PwStudyScreen()
                            else -> BlockerScreen()
                        }
                    }
                }
            }
        }
    }
}
