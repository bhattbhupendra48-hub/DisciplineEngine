package com.bhupendra.disciplineengine.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperOsHelperTest {

    // Verifies Xiaomi, POCO, and Redmi device detection triggers HyperOS onboarding flow
    @Test
    fun detectsXiaomiAndPocoManufacturers() {
        assertTrue(HyperOsOnboardingHelper.isXiaomiOrPocoDevice("Xiaomi", "POCO"))
        assertTrue(HyperOsOnboardingHelper.isXiaomiOrPocoDevice("POCO", "POCO"))
        assertTrue(HyperOsOnboardingHelper.isXiaomiOrPocoDevice("Xiaomi", "Redmi"))
        assertFalse(HyperOsOnboardingHelper.isXiaomiOrPocoDevice("Google", "Pixel"))
    }
}
