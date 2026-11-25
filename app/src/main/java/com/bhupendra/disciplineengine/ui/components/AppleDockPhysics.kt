package com.bhupendra.disciplineengine.ui.components

import kotlin.math.abs
import kotlin.math.exp

// Pure Gaussian-curve dock magnification and swipe-up physics from master_blueprint.json
object AppleDockPhysics {

    const val MAX_SCALE = 1.65f
    const val MIN_SCALE = 1.0f
    const val SCALE_SPAN = 0.65f
    const val POPPED_TRIGGER_SCALE = 1.45f
    const val MAX_LIFT_DP = -28f
    const val SIGMA_DP = 75f
    const val SWIPE_UP_THRESHOLD_DP = 42f

    // Computes icon magnification scale along the Gaussian bell curve
    fun computeGaussianScale(
        fingerX: Float?,
        iconCenterX: Float,
        sigmaPx: Float
    ): Float {
        if (fingerX == null || sigmaPx <= 0f) return MIN_SCALE
        val distance = abs(fingerX - iconCenterX)
        val exponent = -(distance * distance) / (2f * sigmaPx * sigmaPx)
        return (MIN_SCALE + SCALE_SPAN * exp(exponent)).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    // Computes negative vertical translation in dp so hovered icons pop above the dock line
    fun computeVerticalLiftDp(scale: Float): Float {
        val ratio = ((scale - MIN_SCALE) / SCALE_SPAN).coerceIn(0f, 1f)
        return MAX_LIFT_DP * ratio
    }

    // Returns true when an icon is popped (>1.45x) and dragged upward past 42dp
    fun shouldTriggerSwipeUpLaunch(
        scale: Float,
        deltaY: Float,
        swipeUpThresholdPx: Float
    ): Boolean {
        return scale > POPPED_TRIGGER_SCALE && deltaY < -swipeUpThresholdPx
    }
}
