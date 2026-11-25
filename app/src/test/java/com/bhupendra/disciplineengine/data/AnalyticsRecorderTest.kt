package com.bhupendra.disciplineengine.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsRecorderTest {

    // Verifies 24-hour JSON array serialization and parsing stay loss-free
    @Test
    fun roundTripsHourlyAnalyticsArrays() {
        val sample = IntArray(24) { if (it == 21) 45 else 0 }
        val json = AnalyticsRecorder.serializeHourlyArray(sample)
        val parsed = AnalyticsRecorder.parseHourlyArray(json)
        assertEquals(24, parsed.size)
        assertEquals(45, parsed[21])
        assertEquals(0, parsed[0])
    }
}
