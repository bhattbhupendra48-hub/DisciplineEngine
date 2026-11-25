package com.bhupendra.disciplineengine.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushupPoseEngineTest {

    // Builds a synthetic side-profile landmark array with configurable elbow and hip geometry
    private fun buildSideProfileFrame(
        elbowY: Float,
        wristY: Float = 0.85f,
        hipY: Float = 0.55f
    ): List<LandmarkPoint> {
        val list = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0.9f) }
        list[11] = LandmarkPoint(0.30f, 0.55f, 0.95f) // Left shoulder
        list[13] = LandmarkPoint(0.30f, elbowY, 0.95f) // Left elbow
        list[15] = LandmarkPoint(0.30f, wristY, 0.95f) // Left wrist anchored at bottom
        list[23] = LandmarkPoint(0.55f, hipY, 0.95f)   // Left hip
        list[25] = LandmarkPoint(0.70f, 0.55f, 0.95f) // Left knee
        list[27] = LandmarkPoint(0.85f, 0.55f, 0.95f) // Left ankle

        // Right side lower visibility so engine picks left side consistently
        list[12] = LandmarkPoint(0.30f, 0.55f, 0.1f)
        list[14] = LandmarkPoint(0.30f, elbowY, 0.1f)
        list[16] = LandmarkPoint(0.30f, wristY, 0.1f)
        list[24] = LandmarkPoint(0.55f, hipY, 0.1f)
        list[28] = LandmarkPoint(0.85f, 0.55f, 0.1f)
        return list
    }

    // Verifies UP (>155) -> DOWN (<90 held >=150ms) -> UP (>155) increments rep count
    @Test
    fun validPushupCycleCountsRep() {
        val engine = PushupPoseEngine(strictMode = true)
        val upFrame = buildSideProfileFrame(elbowY = 0.70f, wristY = 0.85f)
        // Bent arm: shoulder=(0.30,0.55), elbow=(0.15,0.75), wrist=(0.30,0.85) gives <90 deg
        val downFrame = buildSideProfileFrame(elbowY = 0.75f, wristY = 0.85f).toMutableList().apply {
            this[13] = LandmarkPoint(0.15f, 0.75f, 0.95f)
        }

        val start = engine.evaluateFrame(upFrame, 0L)
        assertTrue(start.isFormValid)
        assertEquals(0, start.repCount)

        engine.evaluateFrame(downFrame, 100L)
        engine.evaluateFrame(downFrame, 280L) // >= 150ms hold
        val finished = engine.evaluateFrame(upFrame, 400L)
        assertTrue(finished.repJustCompleted)
        assertEquals(1, finished.repCount)
    }

    // Verifies hand-waving cheat fails wrist floor anchor rule
    @Test
    fun elevatedWristBlocksRepCounting() {
        val engine = PushupPoseEngine(strictMode = true)
        val cheatingFrame = buildSideProfileFrame(elbowY = 0.40f, wristY = 0.30f)
        val eval = engine.evaluateFrame(cheatingFrame, 0L)
        assertFalse(eval.isFormValid)
    }

    // Verifies sagging hip fails strict 165..195 deg plank check
    @Test
    fun saggingHipsFailStrictPlankCheck() {
        val engine = PushupPoseEngine(strictMode = true)
        val sagFrame = buildSideProfileFrame(elbowY = 0.70f, wristY = 0.85f, hipY = 0.82f)
        val eval = engine.evaluateFrame(sagFrame, 0L)
        assertFalse(eval.isFormValid)
    }
}
