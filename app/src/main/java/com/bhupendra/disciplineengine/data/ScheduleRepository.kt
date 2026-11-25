package com.bhupendra.disciplineengine.data

import com.bhupendra.disciplineengine.domain.RpSource
import com.bhupendra.disciplineengine.domain.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

// Holds loaded profile and timeline blocks for the Schedule Creator screen
data class ScheduleSnapshot(
    val profile: ScheduleProfileEntity,
    val todayBlocks: List<ScheduleBlockEntity>,
    val statusBanner: String? = null
)

class ScheduleRepository(private val dao: DisciplineDao) {

    // Loads today's schedule or generates a fresh one from the saved 7-question profile
    suspend fun loadTodaySchedule(): ScheduleSnapshot = withContext(Dispatchers.IO) {
        val todayEpoch = LocalDate.now().toEpochDay()
        val profile = dao.getScheduleProfile() ?: ScheduleProfileEntity().also {
            dao.upsertScheduleProfile(it)
        }
        var blocks = dao.getScheduleForDay(todayEpoch)
        if (blocks.isEmpty()) {
            blocks = ScheduleEngine.generateDailySchedule(todayEpoch, profile)
            dao.upsertScheduleBlocks(blocks)
        }
        ScheduleSnapshot(profile = profile, todayBlocks = blocks)
    }

    // Saves updated 7-question answers and regenerates today's timeline
    suspend fun saveProfileAndRegenerate(profile: ScheduleProfileEntity): ScheduleSnapshot =
        withContext(Dispatchers.IO) {
            val todayEpoch = LocalDate.now().toEpochDay()
            dao.upsertScheduleProfile(profile)
            dao.deleteScheduleForDay(todayEpoch)
            val generated = ScheduleEngine.generateDailySchedule(todayEpoch, profile)
            dao.upsertScheduleBlocks(generated)
            ScheduleSnapshot(
                profile = profile,
                todayBlocks = generated,
                statusBanner = "Generated ${generated.size} blocks using 7-question energy mapping."
            )
        }

    // Marks a block COMPLETED (awarding RP) or MISSED, then evaluates 8A/8C status
    suspend fun updateBlockStatus(blockId: String, newStatus: String): ScheduleSnapshot =
        withContext(Dispatchers.IO) {
            val todayEpoch = LocalDate.now().toEpochDay()
            val blocks = dao.getScheduleForDay(todayEpoch)
            val target = blocks.firstOrNull { it.blockId == blockId }
            if (target != null && target.status != newStatus) {
                val updated = blocks.map {
                    if (it.blockId == blockId) it.copy(status = newStatus) else it
                }
                dao.upsertScheduleBlocks(updated)
                if (newStatus == "COMPLETED") {
                    val delta = when {
                        target.status == "SALVAGED_15M_SPRINT" -> 22
                        target.priorityTier == "P1_CORE_NON_NEGOTIABLE" -> RpSource.P1_BLOCK.rpDelta
                        target.priorityTier == "P2_IMPORTANT_COMPRESSIBLE" -> RpSource.PW_BLOCK.rpDelta
                        else -> 15
                    }
                    dao.addRpEvent(
                        RpEventEntity(
                            timestamp = System.currentTimeMillis(),
                            source = target.priorityTier,
                            rpDelta = delta
                        )
                    )
                } else if (newStatus == "MISSED" && target.priorityTier == "P1_CORE_NON_NEGOTIABLE") {
                    dao.addRpEvent(
                        RpEventEntity(
                            timestamp = System.currentTimeMillis(),
                            source = RpSource.PENALTY.name,
                            rpDelta = RpSource.PENALTY.rpDelta
                        )
                    )
                }
            }
            val profile = dao.getScheduleProfile() ?: ScheduleProfileEntity()
            ScheduleSnapshot(profile = profile, todayBlocks = dao.getScheduleForDay(todayEpoch))
        }

    // Runs Option 8A + Option 8C auto-reschedule on today's blocks
    suspend fun runAutoReschedule(): ScheduleSnapshot = withContext(Dispatchers.IO) {
        val todayEpoch = LocalDate.now().toEpochDay()
        val blocks = dao.getScheduleForDay(todayEpoch)
        val outcome = ScheduleEngine.applyReschedule8APlus8C(blocks)

        dao.deleteScheduleForDay(todayEpoch)
        dao.upsertScheduleBlocks(outcome.updatedTodayBlocks)
        if (outcome.rolledOverTomorrowBlocks.isNotEmpty()) {
            dao.upsertScheduleBlocks(outcome.rolledOverTomorrowBlocks)
        }

        val profile = dao.getScheduleProfile() ?: ScheduleProfileEntity()
        ScheduleSnapshot(
            profile = profile,
            todayBlocks = outcome.updatedTodayBlocks,
            statusBanner = outcome.summaryMessage
        )
    }
}
