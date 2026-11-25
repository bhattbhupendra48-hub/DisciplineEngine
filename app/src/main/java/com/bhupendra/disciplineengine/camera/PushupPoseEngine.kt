package com.bhupendra.disciplineengine.camera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

// Represents one normalized 2D body landmark from MediaPipe Pose Landmarker
data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val visibility: Float
)

// Holds live rep count, joint angles, and anti-cheat feedback for the overlay UI
data class PoseFrameEvaluation(
    val repCount: Int,
    val elbowAngleDeg: Float,
    val hipAngleDeg: Float,
    val confidence: Float,
    val isFormValid: Boolean,
    val statusHint: String,
    val repJustCompleted: Boolean
)

// Pure BlazePose geometry and anti-cheat pushup rep state machine
class PushupPoseEngine(
    private val strictMode: Boolean = true
) {
    private enum class RepStage { UP, DOWN }

    private var stage: RepStage = RepStage.UP
    private var downEnteredAtMs: Long? = null
    private var repCount: Int = 0
    private var anchoredWristX: Float? = null

    // Evaluates a 33-landmark BlazePose frame and updates rep count if valid
    fun evaluateFrame(landmarks: List<LandmarkPoint>, nowMs: Long): PoseFrameEvaluation {
        if (landmarks.size < 29) {
            return PoseFrameEvaluation(repCount, 0f, 0f, 0f, false, "Stand in side profile", false)
        }

        val leftVis = (landmarks[11].visibility + landmarks[13].visibility + landmarks[15].visibility +
            landmarks[23].visibility + landmarks[27].visibility) / 5f
        val rightVis = (landmarks[12].visibility + landmarks[14].visibility + landmarks[16].visibility +
            landmarks[24].visibility + landmarks[28].visibility) / 5f

        val useLeft = leftVis >= rightVis
        val shoulder = if (useLeft) landmarks[11] else landmarks[12]
        val elbow = if (useLeft) landmarks[13] else landmarks[14]
        val wrist = if (useLeft) landmarks[15] else landmarks[16]
        val hip = if (useLeft) landmarks[23] else landmarks[24]
        val knee = if (useLeft) landmarks[25] else landmarks[26]
        val ankle = if (useLeft) landmarks[27] else landmarks[28]

        val minConfidence = minOf(shoulder.visibility, elbow.visibility, wrist.visibility, hip.visibility)
        if (minConfidence < 0.35f) {
            return PoseFrameEvaluation(
                repCount = repCount,
                elbowAngleDeg = 0f,
                hipAngleDeg = 0f,
                confidence = minConfidence,
                isFormValid = false,
                statusHint = "Low visibility — align side body in frame",
                repJustCompleted = false
            )
        }

        // Wrist anchor check: wrists must stay in bottom 35% of frame and stationary (<6% horizontal drift)
        val anchorX = anchoredWristX ?: wrist.x.also { anchoredWristX = it }
        val wristOnFloor = wrist.y >= 0.65f
        val wristStationary = abs(wrist.x - anchorX) <= 0.06f
        if (!wristOnFloor || !wristStationary) {
            if (!wristStationary) anchoredWristX = wrist.x
            return PoseFrameEvaluation(
                repCount = repCount,
                elbowAngleDeg = computeInteriorAngle(shoulder, elbow, wrist),
                hipAngleDeg = computeStraightnessAngle(shoulder, hip, ankle),
                confidence = minConfidence,
                isFormValid = false,
                statusHint = "Keep hands anchored on floor",
                repJustCompleted = false
            )
        }

        // Plank check: strict 165..195 deg (shoulder-hip-ankle), beginner 145..200 deg (or knee)
        val lowerPoint = if (strictMode || ankle.visibility >= 0.4f) ankle else knee
        val hipAngle = computeStraightnessAngle(shoulder, hip, lowerPoint)
        val plankValid = if (strictMode) {
            hipAngle in 165f..195f
        } else {
            hipAngle in 145f..200f
        }

        val elbowAngle = computeInteriorAngle(shoulder, elbow, wrist)
        if (!plankValid) {
            return PoseFrameEvaluation(
                repCount = repCount,
                elbowAngleDeg = elbowAngle,
                hipAngleDeg = hipAngle,
                confidence = minConfidence,
                isFormValid = false,
                statusHint = "Fix plank alignment (${hipAngle.toInt()} deg)",
                repJustCompleted = false
            )
        }

        // Elbow state machine: UP (>155) -> DOWN (<90 held >= 150ms) -> UP (>155)
        var completed = false
        var hint = "Lower chest (<90 deg)"
        if (stage == RepStage.UP) {
            if (elbowAngle < 90f) {
                val enteredAt = downEnteredAtMs ?: nowMs.also { downEnteredAtMs = it }
                if (nowMs - enteredAt >= 150L) {
                    stage = RepStage.DOWN
                    hint = "Push back up (>155 deg)!"
                } else {
                    hint = "Hold bottom..."
                }
            } else {
                downEnteredAtMs = null
            }
        } else {
            hint = "Lock out arms (>155 deg)"
            if (elbowAngle > 155f) {
                repCount += 1
                stage = RepStage.UP
                downEnteredAtMs = null
                completed = true
                hint = "Rep $repCount counted!"
            }
        }

        return PoseFrameEvaluation(
            repCount = repCount,
            elbowAngleDeg = elbowAngle,
            hipAngleDeg = hipAngle,
            confidence = minConfidence,
            isFormValid = true,
            statusHint = hint,
            repJustCompleted = completed
        )
    }

    // Computes interior joint angle in degrees [0..180]
    fun computeInteriorAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val raw = abs(computeSignedAngle(a, b, c))
        return if (raw > 180f) 360f - raw else raw
    }

    // Computes body straightness angle centered around 180 deg [0..360)
    fun computeStraightnessAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        var angle = computeSignedAngle(a, b, c)
        if (angle < 0f) angle += 360f
        return angle
    }

    private fun computeSignedAngle(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Float {
        val rad = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
            atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        return (rad * 180.0 / PI).toFloat()
    }
}
