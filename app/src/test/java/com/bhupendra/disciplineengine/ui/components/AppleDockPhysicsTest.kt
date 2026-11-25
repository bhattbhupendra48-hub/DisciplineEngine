package com.bhupendra.disciplineengine.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleDockPhysicsTest {

    // Verifies Gaussian bell curve peaks at 1.65x directly under finger and tapers smoothly
    @Test
    fun gaussianScalePeaksAndDecaysCorrectly() {
        val sigma = 150f
        val peak = AppleDockPhysics.computeGaussianScale(200f, 200f, sigma)
        val neighbor = AppleDockPhysics.computeGaussianScale(200f, 350f, sigma)
        val far = AppleDockPhysics.computeGaussianScale(200f, 900f, sigma)

        assertEquals(1.65f, peak, 0.001f)
        assertTrue(neighbor in 1.2f..1.5f)
        assertTrue(far < 1.05f)
        assertEquals(1.0f, AppleDockPhysics.computeGaussianScale(null, 200f, sigma), 0.001f)
    }

    // Verifies vertical lift reaches -28dp at max scale and flick-up triggers launch
    @Test
    fun verticalLiftAndSwipeUpTriggerWork() {
        assertEquals(-28f, AppleDockPhysics.computeVerticalLiftDp(1.65f), 0.01f)
        assertEquals(0f, AppleDockPhysics.computeVerticalLiftDp(1.0f), 0.01f)
        assertTrue(AppleDockPhysics.shouldTriggerSwipeUpLaunch(1.60f, -100f, 80f))
        assertFalse(AppleDockPhysics.shouldTriggerSwipeUpLaunch(1.20f, -100f, 80f))
        assertFalse(AppleDockPhysics.shouldTriggerSwipeUpLaunch(1.60f, -20f, 80f))
    }
}
