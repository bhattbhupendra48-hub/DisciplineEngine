package com.bhupendra.disciplineengine.alarm

// Defines phases of the 3x3 Smart Burst Cycle for empty-room alarm protection
enum class BurstPhase {
    RINGING,
    PAUSED,
    COMPLETED_DISMISSED,
    ABANDONED_TIMEOUT
}

// Snapshot of the current burst cycle state for UI display and audio gating
data class BurstCycleSnapshot(
    val phase: BurstPhase,
    val cycleNumber: Int,
    val totalCycles: Int,
    val phaseRemainingSeconds: Int,
    val motionOrTouchDetected: Boolean
)

// Pure state machine enforcing 3 min ring + 2 min pause x 3 cycles (15 min total)
class BurstCycleStateMachine(
    private val ringDurationMs: Long = 180_000L,
    private val pauseDurationMs: Long = 120_000L,
    private val maxCycles: Int = 3
) {
    private var phase: BurstPhase = BurstPhase.RINGING
    private var cycleNumber: Int = 1
    private var phaseStartMs: Long = 0L
    private var motionOrTouchDetected: Boolean = false

    // Initializes the state machine at the alarm trigger timestamp
    fun start(nowMs: Long): BurstCycleSnapshot {
        phase = BurstPhase.RINGING
        cycleNumber = 1
        phaseStartMs = nowMs
        motionOrTouchDetected = false
        return snapshot(nowMs)
    }

    // Marks physical phone movement (>0.4 m/s^2) or touch interaction
    fun registerInteraction() {
        motionOrTouchDetected = true
    }

    // Marks the alarm dismissed after successful exercise verification
    fun markDismissed(nowMs: Long): BurstCycleSnapshot {
        phase = BurstPhase.COMPLETED_DISMISSED
        return snapshot(nowMs)
    }

    // Advances the burst state machine based on elapsed time
    fun tick(nowMs: Long): BurstCycleSnapshot {
        if (phase == BurstPhase.COMPLETED_DISMISSED || phase == BurstPhase.ABANDONED_TIMEOUT) {
            return snapshot(nowMs)
        }
        val elapsed = (nowMs - phaseStartMs).coerceAtLeast(0L)
        if (phase == BurstPhase.RINGING && elapsed >= ringDurationMs) {
            if (cycleNumber >= maxCycles) {
                phase = BurstPhase.ABANDONED_TIMEOUT
            } else {
                phase = BurstPhase.PAUSED
                phaseStartMs = nowMs
            }
        } else if (phase == BurstPhase.PAUSED && elapsed >= pauseDurationMs) {
            cycleNumber += 1
            phase = BurstPhase.RINGING
            phaseStartMs = nowMs
        }
        return snapshot(nowMs)
    }

    // Produces the current immutable burst status snapshot
    fun snapshot(nowMs: Long): BurstCycleSnapshot {
        val elapsed = (nowMs - phaseStartMs).coerceAtLeast(0L)
        val remainingMs = when (phase) {
            BurstPhase.RINGING -> (ringDurationMs - elapsed).coerceAtLeast(0L)
            BurstPhase.PAUSED -> (pauseDurationMs - elapsed).coerceAtLeast(0L)
            else -> 0L
        }
        return BurstCycleSnapshot(
            phase = phase,
            cycleNumber = cycleNumber,
            totalCycles = maxCycles,
            phaseRemainingSeconds = (remainingMs / 1000L).toInt(),
            motionOrTouchDetected = motionOrTouchDetected
        )
    }
}
