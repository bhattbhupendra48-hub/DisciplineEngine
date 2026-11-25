package com.bhupendra.disciplineengine.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.ui.theme.GlassBorderEnd
import com.bhupendra.disciplineengine.ui.theme.GlassBorderTop
import com.bhupendra.disciplineengine.ui.theme.GlassSurface

// Reusable dark glassmorphic container with subtle cyan-indigo border
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderAccent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (borderAccent != null) {
        Brush.linearGradient(listOf(borderAccent.copy(alpha = 0.7f), GlassBorderEnd))
    } else {
        Brush.linearGradient(listOf(GlassBorderTop, GlassBorderEnd))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassSurface),
        border = BorderStroke(width = 1.dp, brush = borderBrush),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
