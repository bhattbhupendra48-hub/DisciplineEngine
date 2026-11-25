package com.bhupendra.disciplineengine.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bhupendra.disciplineengine.R
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.GlassBorderEnd
import com.bhupendra.disciplineengine.ui.theme.GlassBorderTop
import com.bhupendra.disciplineengine.ui.theme.GlassSurface
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import kotlin.math.abs
import kotlin.math.roundToInt

// Represents one of the 6 primary navigation items in the Apple Morphing Dock
data class DockModuleItem(
    val index: Int,
    val id: String,
    val label: String,
    val iconRes: Int
)

val BLUEPRINT_DOCK_ITEMS = listOf(
    DockModuleItem(0, "nav_home", "HQ & Habits", R.drawable.ic_shield_home),
    DockModuleItem(1, "nav_schedule", "8A/8C Schedule", R.drawable.ic_timeline_calendar),
    DockModuleItem(2, "nav_pw_tracker", "PW Study AI", R.drawable.ic_graduation_bolt),
    DockModuleItem(3, "nav_camera_alarm", "Pushup Arena", R.drawable.ic_camera_dumbbell),
    DockModuleItem(4, "nav_blocker", "Reel Shield", R.drawable.ic_lock_radar),
    DockModuleItem(5, "nav_analytics", "RPG Stats", R.drawable.ic_pentagon_graph)
)

// Apple-style Gaussian magnification dock with horizontal slide-to-pop & vertical flick-up-to-open
@Composable
fun AppleMorphDock(
    selectedIndex: Int,
    onSelectModule: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val sigmaPx = with(density) { AppleDockPhysics.SIGMA_DP.dp.toPx() }
    val swipeUpPx = with(density) { AppleDockPhysics.SWIPE_UP_THRESHOLD_DP.dp.toPx() }

    val iconCentersX = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f) }
    var fingerX by remember { mutableStateOf<Float?>(null) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    var rowOriginX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .padding(bottom = 8.dp)
            .pointerInput(sigmaPx, swipeUpPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var launchedViaFlick = false
                    fingerX = startX

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!launchedViaFlick) {
                                val targetIdx = findNearestIconIndex(change.position.x, iconCentersX, rowOriginX)
                                if (targetIdx >= 0) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectModule(targetIdx)
                                }
                            }
                            fingerX = null
                            hoveredIndex = null
                            break
                        }

                        val currX = change.position.x
                        val currY = change.position.y
                        fingerX = currX
                        val deltaY = currY - startY

                        val nearestIdx = findNearestIconIndex(currX, iconCentersX, rowOriginX)
                        hoveredIndex = nearestIdx
                        if (nearestIdx >= 0 && !launchedViaFlick) {
                            val centerLocal = rowOriginX + iconCentersX[nearestIdx]
                            val scale = AppleDockPhysics.computeGaussianScale(currX, centerLocal, sigmaPx)
                            if (AppleDockPhysics.shouldTriggerSwipeUpLaunch(scale, deltaY, swipeUpPx)) {
                                launchedViaFlick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectModule(nearestIdx)
                            }
                        }
                    }
                }
            }
            // Extra 36.dp bottom gesture track per blueprint spec
            .padding(bottom = 36.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = GlassSurface,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(GlassBorderTop, GlassBorderEnd)))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .onGloballyPositioned { coords ->
                        rowOriginX = coords.positionInParent().x
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BLUEPRINT_DOCK_ITEMS.forEach { item ->
                    val centerLocal = rowOriginX + iconCentersX[item.index]
                    val targetScale = AppleDockPhysics.computeGaussianScale(fingerX, centerLocal, sigmaPx)
                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                        label = "dock_scale_${item.index}"
                    )
                    val liftPx = with(density) {
                        AppleDockPhysics.computeVerticalLiftDp(animatedScale).dp.toPx()
                    }
                    val isSelected = item.index == selectedIndex
                    val isPopped = hoveredIndex == item.index && animatedScale > 1.25f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                val center = coords.positionInParent().x + coords.size.width / 2f
                                if (iconCentersX[item.index] != center) {
                                    iconCentersX[item.index] = center
                                }
                            }
                            .offset { IntOffset(0, liftPx.roundToInt()) }
                            .scale(animatedScale)
                    ) {
                        if (isPopped) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xE6090D16),
                                border = BorderStroke(1.dp, CyberCyan),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    color = XpRankGold,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    color = if (isSelected) CyberCyan.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (isSelected || isPopped) 1.5.dp else 0.5.dp,
                                    color = if (isPopped) XpRankGold else if (isSelected) CyberCyan else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label,
                                tint = if (isPopped) XpRankGold else if (isSelected) CyberCyan else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Finds the closest dock icon index to the current finger X position
private fun findNearestIconIndex(fingerX: Float, centersX: List<Float>, rowOriginX: Float): Int {
    if (centersX.isEmpty()) return -1
    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    centersX.forEachIndexed { idx, cx ->
        val dist = abs(fingerX - (rowOriginX + cx))
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
    }
    return bestIdx
}
