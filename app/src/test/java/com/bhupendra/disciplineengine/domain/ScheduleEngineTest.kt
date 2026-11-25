package com.bhupendra.disciplineengine.domain

import com.bhupendra.disciplineengine.data.ScheduleProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    // Verifies 7-question schedule generator inserts 8A buffer and 10m transition breaks
    @Test
    fun generatesBufferAndTransitionBreaks() {
        val profile = ScheduleProfileEntity(p1TargetMinutes = 90, p2TargetMinutes = 60)
        val blocks = ScheduleEngine.generateDailySchedule(100L, profile)

        assertTrue(blocks.any { it.blockType == "SHOCK_ABSORBER_BUFFER_8A" })
        assertTrue(blocks.any { it.blockType == "TRANSITION_BREAK" && it.durationMinutes == 10 })
        assertTrue(blocks.any { it.priorityTier == "P1_CORE_NON_NEGOTIABLE" })
    }

    // Verifies Layer 1 (8A) slides short missed tasks directly into the 60m Flex Buffer
    @Test
    fun option8ASlidesShortMissedTaskIntoBuffer() {
        val blocks = ScheduleEngine.generateDailySchedule(100L, ScheduleProfileEntity(p3TargetMinutes = 45))
        val marked = blocks.map {
            if (it.priorityTier == "P3_BONUS_ROLLOVER" && it.blockType == "FLEX_TASK") {
                it.copy(status = "MISSED")
            } else {
                it
            }
        }

        val outcome = ScheduleEngine.applyReschedule8APlus8C(marked)
        assertTrue(outcome.summaryMessage.contains("Layer 1 (8A)"))
        assertTrue(outcome.rolledOverTomorrowBlocks.isEmpty())
        assertTrue(outcome.updatedTodayBlocks.any { it.title.contains("8A Salvaged") })
    }

    // Verifies Layer 2 (8C) protects anchors, prioritizes P1, salvages P2 to 15m, and rolls P3
    @Test
    fun option8CTriagesOverflowCorrectly() {
        val blocks = ScheduleEngine.generateDailySchedule(
            100L,
            ScheduleProfileEntity(p1TargetMinutes = 90, p2TargetMinutes = 90, p3TargetMinutes = 45)
        )
        val marked = blocks.map {
            if (it.blockType == "FLEX_TASK") it.copy(status = "MISSED") else it
        }

        val outcome = ScheduleEngine.applyReschedule8APlus8C(marked)
        assertTrue(outcome.summaryMessage.contains("Layer 2 (8C Triage)"))
        assertTrue(outcome.updatedTodayBlocks.any { it.status == "SALVAGED_15M_SPRINT" && it.durationMinutes == 15 })
        assertEquals(1, outcome.rolledOverTomorrowBlocks.size)
    }

    // Verifies hard-lock window returns true during danger hours
    @Test
    fun dangerWindowTriggersHardLock() {
        val profile = ScheduleProfileEntity(dangerStartMinutes = 1290, dangerEndMinutes = 1380)
        assertTrue(ScheduleEngine.isHardLockWindowActive(profile, emptyList(), 1320))
    }
}
