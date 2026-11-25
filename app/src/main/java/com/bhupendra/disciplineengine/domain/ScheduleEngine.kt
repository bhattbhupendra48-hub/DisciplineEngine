package com.bhupendra.disciplineengine.domain

import com.bhupendra.disciplineengine.data.ScheduleBlockEntity
import com.bhupendra.disciplineengine.data.ScheduleProfileEntity
import java.util.Locale

// Holds the result of running the 8A + 8C reschedule engine on a day's blocks
data class RescheduleOutcome(
    val updatedTodayBlocks: List<ScheduleBlockEntity>,
    val rolledOverTomorrowBlocks: List<ScheduleBlockEntity>,
    val summaryMessage: String
)

object ScheduleEngine {

    private const val BUFFER_8A_DURATION = 60
    private const val TRANSITION_BREAK_DURATION = 10
    private const val P2_SALVAGE_DURATION = 15

    // Generates an energy-matched daily schedule with anchors, 8A buffer, and 10m transition breaks
    fun generateDailySchedule(epochDay: Long, profile: ScheduleProfileEntity): List<ScheduleBlockEntity> {
        val anchors = mutableListOf(
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_alarm",
                dateEpochDay = epochDay,
                title = "Morning Pushup Alarm",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "PHYSICAL_TRAINING",
                startTimeMinutesFromMidnight = profile.wakeUpMinutes,
                durationMinutes = 20,
                status = "PENDING"
            ),
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_coaching",
                dateEpochDay = epochDay,
                title = "School / Coaching Batch",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "STEADY_WORK",
                startTimeMinutesFromMidnight = profile.coachingStartMinutes,
                durationMinutes = (profile.coachingEndMinutes - profile.coachingStartMinutes).coerceAtLeast(30),
                status = "PENDING"
            ),
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_lunch",
                dateEpochDay = epochDay,
                title = "Lunch & Recharge",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "LOW_DRAIN_ADMIN",
                startTimeMinutesFromMidnight = profile.lunchMinutes,
                durationMinutes = 30,
                status = "PENDING"
            ),
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_sports",
                dateEpochDay = epochDay,
                title = "Sports / Outdoor Training",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "PHYSICAL_TRAINING",
                startTimeMinutesFromMidnight = profile.sportsStartMinutes,
                durationMinutes = (profile.sportsEndMinutes - profile.sportsStartMinutes).coerceAtLeast(30),
                status = "PENDING"
            ),
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_dinner",
                dateEpochDay = epochDay,
                title = "Dinner & Family",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "LOW_DRAIN_ADMIN",
                startTimeMinutesFromMidnight = profile.dinnerMinutes,
                durationMinutes = 30,
                status = "PENDING"
            )
        ).sortedBy { it.startTimeMinutesFromMidnight }.toMutableList()

        // Reserve 60-min 8A Shock-Absorber Buffer in evening before dinner or after sports
        val bufferStart = findFreeSlot(
            occupied = anchors,
            preferredStart = (profile.dinnerMinutes - BUFFER_8A_DURATION).coerceAtLeast(profile.sportsEndMinutes),
            duration = BUFFER_8A_DURATION,
            minTime = profile.wakeUpMinutes + 20,
            maxTime = profile.sleepMinutes
        )
        if (bufferStart != null) {
            anchors.add(
                ScheduleBlockEntity(
                    blockId = "${epochDay}_buffer_8a",
                    dateEpochDay = epochDay,
                    title = "8A Shock-Absorber Flex Buffer",
                    blockType = "SHOCK_ABSORBER_BUFFER_8A",
                    priorityTier = "P2_IMPORTANT_COMPRESSIBLE",
                    energyTag = "STEADY_WORK",
                    startTimeMinutesFromMidnight = bufferStart,
                    durationMinutes = BUFFER_8A_DURATION,
                    status = "PENDING"
                )
            )
        }

        // Slot P1 Deep Study during peak energy hours
        val p1Start = findFreeSlot(
            occupied = anchors,
            preferredStart = profile.peakFocusStartMinutes,
            duration = profile.p1TargetMinutes,
            minTime = profile.wakeUpMinutes + 20,
            maxTime = profile.sleepMinutes
        )
        if (p1Start != null) {
            anchors.add(
                ScheduleBlockEntity(
                    blockId = "${epochDay}_p1_core",
                    dateEpochDay = epochDay,
                    title = "P1 Core Deep Study Block",
                    blockType = "FLEX_TASK",
                    priorityTier = "P1_CORE_NON_NEGOTIABLE",
                    energyTag = "PEAK_DEEP_WORK",
                    startTimeMinutesFromMidnight = p1Start,
                    durationMinutes = profile.p1TargetMinutes,
                    status = "PENDING"
                )
            )
            if (profile.p1TargetMinutes >= 60) {
                val breakStart = p1Start + profile.p1TargetMinutes
                if (isRangeFree(anchors, breakStart, TRANSITION_BREAK_DURATION) && breakStart + TRANSITION_BREAK_DURATION <= profile.sleepMinutes) {
                    anchors.add(
                        ScheduleBlockEntity(
                            blockId = "${epochDay}_break_p1",
                            dateEpochDay = epochDay,
                            title = "Water & Stretch Transition",
                            blockType = "TRANSITION_BREAK",
                            priorityTier = "P3_BONUS_ROLLOVER",
                            energyTag = "LOW_DRAIN_ADMIN",
                            startTimeMinutesFromMidnight = breakStart,
                            durationMinutes = TRANSITION_BREAK_DURATION,
                            status = "PENDING"
                        )
                    )
                }
            }
        }

        // Slot P2 Important Block (PW Lecture / DPP) after coaching or afternoon dip
        val p2Start = findFreeSlot(
            occupied = anchors,
            preferredStart = (profile.afternoonDipStartMinutes + 30).coerceAtLeast(profile.coachingEndMinutes),
            duration = profile.p2TargetMinutes,
            minTime = profile.wakeUpMinutes + 20,
            maxTime = profile.sleepMinutes
        )
        if (p2Start != null) {
            anchors.add(
                ScheduleBlockEntity(
                    blockId = "${epochDay}_p2_pw",
                    dateEpochDay = epochDay,
                    title = "P2 PW Lecture + Active DPP",
                    blockType = "FLEX_TASK",
                    priorityTier = "P2_IMPORTANT_COMPRESSIBLE",
                    energyTag = "STEADY_WORK",
                    startTimeMinutesFromMidnight = p2Start,
                    durationMinutes = profile.p2TargetMinutes,
                    status = "PENDING"
                )
            )
            if (profile.p2TargetMinutes >= 60) {
                val breakStart = p2Start + profile.p2TargetMinutes
                if (isRangeFree(anchors, breakStart, TRANSITION_BREAK_DURATION) && breakStart + TRANSITION_BREAK_DURATION <= profile.sleepMinutes) {
                    anchors.add(
                        ScheduleBlockEntity(
                            blockId = "${epochDay}_break_p2",
                            dateEpochDay = epochDay,
                            title = "Eye Rest & Hydration Break",
                            blockType = "TRANSITION_BREAK",
                            priorityTier = "P3_BONUS_ROLLOVER",
                            energyTag = "LOW_DRAIN_ADMIN",
                            startTimeMinutesFromMidnight = breakStart,
                            durationMinutes = TRANSITION_BREAK_DURATION,
                            status = "PENDING"
                        )
                    )
                }
            }
        }

        // Slot P3 Bonus Revision in remaining open slot
        val p3Start = findFreeSlot(
            occupied = anchors,
            preferredStart = profile.dangerStartMinutes,
            duration = profile.p3TargetMinutes,
            minTime = profile.wakeUpMinutes + 20,
            maxTime = profile.sleepMinutes
        )
        if (p3Start != null) {
            anchors.add(
                ScheduleBlockEntity(
                    blockId = "${epochDay}_p3_bonus",
                    dateEpochDay = epochDay,
                    title = "P3 Formula & Flashcard Revision",
                    blockType = "FLEX_TASK",
                    priorityTier = "P3_BONUS_ROLLOVER",
                    energyTag = "LOW_DRAIN_ADMIN",
                    startTimeMinutesFromMidnight = p3Start,
                    durationMinutes = profile.p3TargetMinutes,
                    status = "PENDING"
                )
            )
        }

        // Sleep anchor marker
        anchors.add(
            ScheduleBlockEntity(
                blockId = "${epochDay}_anchor_sleep",
                dateEpochDay = epochDay,
                title = "Hard Sleep Anchor",
                blockType = "FIXED_ANCHOR",
                priorityTier = "ANCHOR_IMMUTABLE",
                energyTag = "LOW_DRAIN_ADMIN",
                startTimeMinutesFromMidnight = profile.sleepMinutes,
                durationMinutes = 30,
                status = "PENDING"
            )
        )

        return anchors.sortedBy { it.startTimeMinutesFromMidnight }
    }

    // Applies Layer 1 (8A Shock-Absorber Buffer) and Layer 2 (8C Priority Triage) to missed tasks
    fun applyReschedule8APlus8C(blocks: List<ScheduleBlockEntity>): RescheduleOutcome {
        val sorted = blocks.sortedBy { it.startTimeMinutesFromMidnight }.toMutableList()
        val bufferIndex = sorted.indexOfFirst { it.blockType == "SHOCK_ABSORBER_BUFFER_8A" }
        val missedFlex = sorted.filter {
            it.status == "MISSED" && it.priorityTier != "ANCHOR_IMMUTABLE" && it.blockType != "TRANSITION_BREAK"
        }

        if (missedFlex.isEmpty()) {
            val noMissesAllDone = sorted.none {
                it.blockType == "FLEX_TASK" && it.status in listOf("PENDING", "ACTIVE", "MISSED")
            }
            if (noMissesAllDone && bufferIndex >= 0) {
                sorted[bufferIndex] = sorted[bufferIndex].copy(
                    title = "Bonus Free Time (All Tasks On Time!)",
                    status = "COMPLETED"
                )
                return RescheduleOutcome(sorted, emptyList(), "8A Buffer unlocked as Bonus Leisure!")
            }
            return RescheduleOutcome(sorted, emptyList(), "No missed blocks to reschedule.")
        }

        val totalMissedMinutes = missedFlex.sumOf { it.durationMinutes }
        val bufferBlock = if (bufferIndex >= 0) sorted[bufferIndex] else null
        val bufferCapacity = bufferBlock?.durationMinutes ?: 0

        // Layer 1 (8A): All missed blocks fit inside the evening Shock-Absorber Buffer
        if (bufferBlock != null && totalMissedMinutes <= bufferCapacity) {
            var cursor = bufferBlock.startTimeMinutesFromMidnight
            val replacedBufferTasks = missedFlex.map { missed ->
                val shifted = missed.copy(
                    blockId = "${missed.blockId}_8a",
                    startTimeMinutesFromMidnight = cursor,
                    status = "ACTIVE",
                    title = "${missed.title} [8A Salvaged]"
                )
                cursor += missed.durationMinutes
                shifted
            }
            sorted.removeAll { it.blockId == bufferBlock.blockId }
            sorted.removeAll { missed -> missedFlex.any { it.blockId == missed.blockId } }
            sorted.addAll(replacedBufferTasks)
            return RescheduleOutcome(
                updatedTodayBlocks = sorted.sortedBy { it.startTimeMinutesFromMidnight },
                rolledOverTomorrowBlocks = emptyList(),
                summaryMessage = "Layer 1 (8A): Slotted ${missedFlex.size} missed block(s) into Flex Buffer."
            )
        }

        // Layer 2 (8C Priority Triage): Delays exceed 60 min buffer
        val tomorrowRollover = mutableListOf<ScheduleBlockEntity>()
        var remainingBufferCursor = bufferBlock?.startTimeMinutesFromMidnight ?: 0
        var remainingBufferMins = bufferCapacity
        val triaged = mutableListOf<ScheduleBlockEntity>()

        for (block in sorted) {
            if (block.blockId == bufferBlock?.blockId) continue

            if (block.status != "MISSED" || block.priorityTier == "ANCHOR_IMMUTABLE") {
                triaged.add(block)
                continue
            }

            when (block.priorityTier) {
                "P1_CORE_NON_NEGOTIABLE" -> {
                    val assignedDur = if (remainingBufferMins > 0) {
                        minOf(block.durationMinutes, remainingBufferMins)
                    } else {
                        block.durationMinutes
                    }
                    val startAt = if (remainingBufferMins > 0) remainingBufferCursor else block.startTimeMinutesFromMidnight
                    if (remainingBufferMins > 0) {
                        remainingBufferCursor += assignedDur
                        remainingBufferMins -= assignedDur
                    }
                    triaged.add(
                        block.copy(
                            startTimeMinutesFromMidnight = startAt,
                            durationMinutes = assignedDur,
                            status = "ACTIVE",
                            title = "${block.title} [8C P1 Priority]"
                        )
                    )
                }
                "P2_IMPORTANT_COMPRESSIBLE" -> {
                    val startAt = if (remainingBufferMins >= P2_SALVAGE_DURATION) {
                        val s = remainingBufferCursor
                        remainingBufferCursor += P2_SALVAGE_DURATION
                        remainingBufferMins -= P2_SALVAGE_DURATION
                        s
                    } else {
                        block.startTimeMinutesFromMidnight
                    }
                    triaged.add(
                        block.copy(
                            startTimeMinutesFromMidnight = startAt,
                            durationMinutes = P2_SALVAGE_DURATION,
                            status = "SALVAGED_15M_SPRINT",
                            title = "${block.title} [15m Rapid Salvage Sprint]"
                        )
                    )
                }
                else -> {
                    triaged.add(block.copy(status = "ROLLED_OVER"))
                    tomorrowRollover.add(
                        block.copy(
                            blockId = "${block.blockId}_roll_${block.dateEpochDay + 1}",
                            dateEpochDay = block.dateEpochDay + 1,
                            status = "PENDING",
                            title = "${block.title} [Rolled Over]"
                        )
                    )
                }
            }
        }

        return RescheduleOutcome(
            updatedTodayBlocks = triaged.sortedBy { it.startTimeMinutesFromMidnight },
            rolledOverTomorrowBlocks = tomorrowRollover,
            summaryMessage = "Layer 2 (8C Triage): P1 prioritized, P2 compressed to 15m Sprint, P3 rolled over."
        )
    }

    // Determines if study hard-lock or danger-hour protection is active at the current minute
    fun isHardLockWindowActive(
        profile: ScheduleProfileEntity,
        blocks: List<ScheduleBlockEntity>,
        currentMinuteOfDay: Int
    ): Boolean {
        val inDangerHours = currentMinuteOfDay in profile.dangerStartMinutes..profile.dangerEndMinutes
        val inActiveStudyBlock = blocks.any { block ->
            block.blockType == "FLEX_TASK" &&
                (block.priorityTier == "P1_CORE_NON_NEGOTIABLE" || block.priorityTier == "P2_IMPORTANT_COMPRESSIBLE") &&
                block.status in listOf("PENDING", "ACTIVE", "SALVAGED_15M_SPRINT") &&
                currentMinuteOfDay in block.startTimeMinutesFromMidnight..(block.startTimeMinutesFromMidnight + block.durationMinutes)
        }
        return inDangerHours || inActiveStudyBlock
    }

    // Formats minutes from midnight into readable 12-hour clock text
    fun formatMinutesAsTime(minutesFromMidnight: Int): String {
        val clamped = ((minutesFromMidnight % 1440) + 1440) % 1440
        val h24 = clamped / 60
        val m = clamped % 60
        val amPm = if (h24 >= 12) "PM" else "AM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.US, "%02d:%02d %s", h12, m, amPm)
    }

    // Scans outward from preferredStart for a non-overlapping schedule slot
    private fun findFreeSlot(
        occupied: List<ScheduleBlockEntity>,
        preferredStart: Int,
        duration: Int,
        minTime: Int,
        maxTime: Int
    ): Int? {
        if (duration <= 0) return null
        val candidateStarts = sequence {
            yield(preferredStart.coerceIn(minTime, (maxTime - duration).coerceAtLeast(minTime)))
            var t = minTime
            while (t + duration <= maxTime) {
                yield(t)
                t += 15
            }
        }
        return candidateStarts.firstOrNull { start ->
            start >= minTime && start + duration <= maxTime && isRangeFree(occupied, start, duration)
        }
    }

    // Checks whether a proposed time range does not overlap any existing block
    private fun isRangeFree(occupied: List<ScheduleBlockEntity>, start: Int, duration: Int): Boolean {
        val end = start + duration
        return occupied.none { existing ->
            val eStart = existing.startTimeMinutesFromMidnight
            val eEnd = eStart + existing.durationMinutes
            start < eEnd && end > eStart
        }
    }
}
