package com.bhupendra.disciplineengine.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

// Defines the active exercise verification mode driven by hardware watchdogs
enum class VerifyMode {
    CAMERA_BLAZEPOSE,
    PROXIMITY_ACCELEROMETER,
    DARK_ROOM_GYRO_SQUAT
}

// Manages 4s camera hang watchdog, <5 lux torch + 1.5x gyro squats, and proximity fallback
class ExerciseWatchdogManager(
    context: Context,
    private val basePushupsRequired: Int,
    private val onModeChanged: (VerifyMode, String) -> Unit,
    private val onDarkRoomTorchChanged: (Boolean, Boolean) -> Unit,
    private val onFallbackRepCounted: (Int) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var linearAccelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var activeMode: VerifyMode = VerifyMode.CAMERA_BLAZEPOSE
    private var cameraBound: Boolean = false
    private var createdAtMs: Long = 0L
    private var lowFpsStreakMs: Long = 0L
    private var lastFrameCheckMs: Long = 0L
    private var framesInWindow: Int = 0

    // Dark room state (<5 lux + confidence < 0.35 for 5 seconds)
    private var currentLux: Float = 100f
    private var darkStartMs: Long? = null
    private var torchActive: Boolean = false
    private var darkSquatUnlocked: Boolean = false

    // Proximity + Accelerometer pushup rep tracking
    private var isChestNearSensor: Boolean = false
    private var lastAccelImpulseMs: Long = 0L
    private var fallbackReps: Int = 0

    // Gyroscope + Linear Accel squat rep tracking
    private var squatDownDetected: Boolean = false
    private var lastSquatRepMs: Long = 0L

    // Starts ambient light monitoring and arms the 4-second camera hang watchdog
    fun startMonitoring(nowMs: Long = System.currentTimeMillis()) {
        createdAtMs = nowMs
        lastFrameCheckMs = nowMs
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    // Stops all registered hardware sensors cleanly
    fun stopMonitoring() {
        sensorManager?.unregisterListener(this)
    }

    // Signals that CameraX successfully bound to lifecycle
    fun markCameraBound() {
        cameraBound = true
    }

    // Tracks processed ML frames and evaluates 4s startup hang and <5 FPS watchdog
    fun recordFrameAndEvaluateWatchdog(
        landmarkConfidence: Float,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (activeMode != VerifyMode.CAMERA_BLAZEPOSE) return

        framesInWindow += 1
        if (!cameraBound && nowMs - createdAtMs >= 4000L) {
            triggerProximityWatchdog("Camera failed to bind in 4s — switched to Proximity mode")
            return
        }

        val elapsedWindow = nowMs - lastFrameCheckMs
        if (elapsedWindow >= 1000L) {
            val fps = framesInWindow * 1000f / elapsedWindow.toFloat()
            framesInWindow = 0
            lastFrameCheckMs = nowMs
            if (fps < 5.0f) {
                lowFpsStreakMs += elapsedWindow
                if (lowFpsStreakMs >= 3000L) {
                    triggerProximityWatchdog("Camera <5 FPS for 3s — switched to Proximity mode")
                    return
                }
            } else {
                lowFpsStreakMs = 0L
            }
        }

        evaluateDarkRoomCondition(landmarkConfidence, nowMs)
    }

    // Checks 4-second startup hang even when zero frames arrive
    fun checkStartupTimeout(nowMs: Long = System.currentTimeMillis()) {
        if (activeMode == VerifyMode.CAMERA_BLAZEPOSE && !cameraBound && nowMs - createdAtMs >= 4000L) {
            triggerProximityWatchdog("Camera hang detected (>4s) — Proximity mode enabled")
        }
    }

    // Transitions into Dark Room Gyroscope Squat mode with 1.5x effort multiplier
    fun activateDarkRoomSquatMode(): Int {
        activeMode = VerifyMode.DARK_ROOM_GYRO_SQUAT
        sensorManager?.unregisterListener(this)
        linearAccelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        val squatTarget = ceil(basePushupsRequired * 1.5f).toInt()
        onModeChanged(activeMode, "Dark Room Squat Mode (1.5x Effort: $squatTarget squats)")
        return squatTarget
    }

    // Processes proximity, light, accelerometer, and gyroscope sensor events
    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        val nowMs = System.currentTimeMillis()
        when (e.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                currentLux = e.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                if (activeMode != VerifyMode.PROXIMITY_ACCELEROMETER) return
                val maxRange = proximitySensor?.maximumRange ?: 5f
                val threshold = minOf(5f, maxRange)
                val near = e.values[0] < threshold
                if (near && !isChestNearSensor) {
                    isChestNearSensor = true
                } else if (!near && isChestNearSensor && nowMs - lastAccelImpulseMs < 2000L) {
                    isChestNearSensor = false
                    fallbackReps += 1
                    onFallbackRepCounted(fallbackReps)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (activeMode != VerifyMode.PROXIMITY_ACCELEROMETER) return
                val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                if (abs(mag - 9.81f) > 1.2f) {
                    lastAccelImpulseMs = nowMs
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (activeMode != VerifyMode.DARK_ROOM_GYRO_SQUAT) return
                val verticalAccel = e.values[1]
                if (verticalAccel < -1.8f) {
                    squatDownDetected = true
                } else if (verticalAccel > 1.8f && squatDownDetected && nowMs - lastSquatRepMs >= 650L) {
                    squatDownDetected = false
                    lastSquatRepMs = nowMs
                    fallbackReps += 1
                    onFallbackRepCounted(fallbackReps)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (activeMode != VerifyMode.DARK_ROOM_GYRO_SQUAT) return
                val pitchRate = abs(e.values[0])
                if (pitchRate > 1.0f && squatDownDetected && nowMs - lastSquatRepMs >= 650L) {
                    squatDownDetected = false
                    lastSquatRepMs = nowMs
                    fallbackReps += 1
                    onFallbackRepCounted(fallbackReps)
                }
            }
        }
    }

    // Unused SensorEventListener callback
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // Activates automatic Proximity + Accelerometer fallback when camera hangs
    private fun triggerProximityWatchdog(reason: String) {
        activeMode = VerifyMode.PROXIMITY_ACCELEROMETER
        sensorManager?.unregisterListener(this)
        proximitySensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        onModeChanged(activeMode, reason)
    }

    // Evaluates <5 lux dark room screen torch and 5-second squat fallback unlock
    private fun evaluateDarkRoomCondition(landmarkConfidence: Float, nowMs: Long) {
        if (currentLux < 5.0f && landmarkConfidence < 0.35f) {
            if (!torchActive) {
                torchActive = true
                darkStartMs = nowMs
                onDarkRoomTorchChanged(true, false)
            } else {
                val started = darkStartMs ?: nowMs
                if (!darkSquatUnlocked && nowMs - started >= 5000L) {
                    darkSquatUnlocked = true
                    onDarkRoomTorchChanged(true, true)
                }
            }
        } else if (torchActive && currentLux >= 8.0f) {
            torchActive = false
            darkStartMs = null
            darkSquatUnlocked = false
            onDarkRoomTorchChanged(false, false)
        }
    }
}
