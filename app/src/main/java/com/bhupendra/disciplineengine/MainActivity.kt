package com.bhupendra.disciplineengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bhupendra.disciplineengine.ui.screens.DashboardScreen
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme

class MainActivity : ComponentActivity() {
    // Hosts the root Compose UI shell with Dark Glassmorphism theme
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
