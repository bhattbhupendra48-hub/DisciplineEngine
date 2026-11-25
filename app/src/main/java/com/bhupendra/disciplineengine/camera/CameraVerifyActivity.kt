package com.bhupendra.disciplineengine.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bhupendra.disciplineengine.DisciplineApp
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.ui.components.GlassCard
import com.bhupendra.disciplineengine.ui.theme.BackgroundCanvas
import com.bhupendra.disciplineengine.ui.theme.CyberCyan
import com.bhupendra.disciplineengine.ui.theme.DisciplineTheme
import com.bhupendra.disciplineengine.ui.theme.P1CriticalNeon
import com.bhupendra.disciplineengine.ui.theme.VerifiedEmerald
import com.bhupendra.disciplineengine.ui.theme.XpRankGold
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// On-demand BlazePose-Lite (640x480 @ 15 FPS) verifier with 4s hang & <5 lux watchdogs
class CameraVerifyActivity : ComponentActivity() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var cameraExecutor: ExecutorService? = null
    private var watchdogManager: ExerciseWatchdogManager? = null
    private var poseEngine = PushupPoseEngine(strictMode = true)
    private var toneGenerator: ToneGenerator? = null

    private var targetReps by mutableIntStateOf(20)
    private var currentReps by mutableIntStateOf(0)
    private var verifyMode by mutableStateOf(VerifyMode.CAMERA_BLAZEPOSE)
    private var statusMessage by mutableStateOf("Prop phone against wall showing side profile")
    private var isFormValid by mutableStateOf(true)
    private var torchActive by mutableStateOf(false)
    private var darkSquatUnlocked by mutableStateOf(false)
    private var tokensRemaining by mutableIntStateOf(2)
    private var lastAnalyzedMs: Long = 0L
    private var previewView: PreviewView? = null
    private var completedOnce = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraAndModel()
        } else {
            watchdogManager?.checkStartupTimeout(System.currentTimeMillis() + 5000L)
        }
    }

    // Initializes UI, hardware watchdogs, and requests camera permission
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val strict = intent.getBooleanExtra(EXTRA_STRICT_MODE, true)
        targetReps = intent.getIntExtra(EXTRA_TARGET_REPS, 20).coerceAtLeast(1)
        poseEngine = PushupPoseEngine(strictMode = strict)
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

        lifecycleScope.launch {
            val streak = DisciplineDatabase.get(DisciplineApp.appContext).dao().getStreak()
            tokensRemaining = streak?.monthlyDarkRoomBypassTokensLeft ?: 2
        }

        watchdogManager = ExerciseWatchdogManager(
            context = this,
            basePushupsRequired = targetReps,
            onModeChanged = { mode, reason ->
                runOnUiThread {
                    verifyMode = mode
                    statusMessage = reason
                    releaseCameraAndMl()
                }
            },
            onDarkRoomTorchChanged = { torchOn, squatReady ->
                runOnUiThread {
                    torchActive = torchOn
                    darkSquatUnlocked = squatReady
                    setScreenBrightness(if (torchOn) 1.0f else -1.0f)
                }
            },
            onFallbackRepCounted = { reps ->
                runOnUiThread {
                    currentReps = reps
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                    checkGoalCompletion()
                }
            }
        )

        setContent {
            DisciplineTheme {
                val borderModifier = when {
                    torchActive -> Modifier.border(40.dp, Color.White)
                    !isFormValid && verifyMode == VerifyMode.CAMERA_BLAZEPOSE -> Modifier.border(8.dp, P1CriticalNeon)
                    else -> Modifier
                }

                Surface(Modifier.fillMaxSize(), color = BackgroundCanvas) {
                    Box(Modifier.fillMaxSize().then(borderModifier)) {
                        if (verifyMode == VerifyMode.CAMERA_BLAZEPOSE) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    PreviewView(ctx).also { view ->
                                        previewView = view
                                        if (hasCameraPermission()) {
                                            startCameraAndModel()
                                        }
                                    }
                                }
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(BackgroundCanvas),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (verifyMode == VerifyMode.PROXIMITY_ACCELEROMETER) {
                                        "PROXIMITY MODE ACTIVE\nLay phone flat beneath chest"
                                    } else {
                                        "DARK ROOM GYRO SQUAT MODE\nHold phone to chest and squat"
                                    },
                                    color = XpRankGold,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            GlassCard(borderAccent = if (isFormValid) VerifiedEmerald else P1CriticalNeon) {
                                Text(
                                    text = "PUSHUP ARENA • ${verifyMode.name}",
                                    color = CyberCyan,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "$currentReps / $targetReps REPS",
                                    color = XpRankGold,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = statusMessage,
                                    color = if (isFormValid) Color.White else P1CriticalNeon,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (darkSquatUnlocked && tokensRemaining > 0 && verifyMode == VerifyMode.CAMERA_BLAZEPOSE) {
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { activateDarkRoomSquats() }
                                    ) {
                                        Text("Dark Room Gyro Squats (1.5x Effort, $tokensRemaining/2 left)")
                                    }
                                }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { finishCancelled() }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Starts watchdogs and requests camera permission on resume
    override fun onStart() {
        super.onStart()
        watchdogManager?.startMonitoring()
        lifecycleScope.launch {
            delay(4100L)
            watchdogManager?.checkStartupTimeout()
        }
        if (verifyMode == VerifyMode.CAMERA_BLAZEPOSE) {
            if (hasCameraPermission()) {
                startCameraAndModel()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Ironclad Law #2: Releases 100% of CameraX and MediaPipe memory immediately in onStop
    override fun onStop() {
        super.onStop()
        watchdogManager?.stopMonitoring()
        setScreenBrightness(-1.0f)
        releaseCameraAndMl()
    }

    // Frees tone generator resources when activity is destroyed
    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    // Initializes BlazePose-Lite interpreter and binds 640x480 @ 15 FPS CameraX pipeline
    private fun startCameraAndModel() {
        if (verifyMode != VerifyMode.CAMERA_BLAZEPOSE || cameraProvider != null) return
        val pv = previewView ?: return

        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (_: Exception) {
            watchdogManager?.checkStartupTimeout(System.currentTimeMillis() + 5000L)
            return
        }

        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = pv.surfaceProvider
                }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(cameraExecutor!!) { proxy ->
                    analyzeFrameThrottled(proxy)
                }

                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, analysis)
                watchdogManager?.markCameraBound()
            } catch (_: Exception) {
                watchdogManager?.checkStartupTimeout(System.currentTimeMillis() + 5000L)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Caps ML inference at 15 FPS and evaluates BlazePose landmarks
    private fun analyzeFrameThrottled(imageProxy: ImageProxy) {
        try {
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastAnalyzedMs < 66L) return
            lastAnalyzedMs = nowMs

            val landmarker = poseLandmarker ?: return
            val rawBitmap = imageProxy.toBitmap()
            val rotated = rotateBitmap(rawBitmap, imageProxy.imageInfo.rotationDegrees)
            if (rotated !== rawBitmap) rawBitmap.recycle()

            val mpImage = BitmapImageBuilder(rotated).build()
            val result = landmarker.detect(mpImage)
            rotated.recycle()

            val landmarks = result.landmarks().firstOrNull()?.map { lm ->
                LandmarkPoint(
                    x = lm.x(),
                    y = lm.y(),
                    visibility = lm.visibility().orElse(0f)
                )
            } ?: emptyList()

            val eval = poseEngine.evaluateFrame(landmarks, nowMs)
            watchdogManager?.recordFrameAndEvaluateWatchdog(eval.confidence, nowMs)

            runOnUiThread {
                currentReps = eval.repCount
                isFormValid = eval.isFormValid
                statusMessage = eval.statusHint
                if (eval.repJustCompleted) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                    checkGoalCompletion()
                }
            }
        } catch (_: Exception) {
            watchdogManager?.recordFrameAndEvaluateWatchdog(0f, System.currentTimeMillis())
        } finally {
            imageProxy.close()
        }
    }

    // Consumes 1 monthly Dark Room bypass token and starts 1.5x Gyroscope Squat mode
    private fun activateDarkRoomSquats() {
        lifecycleScope.launch {
            val dao = DisciplineDatabase.get(DisciplineApp.appContext).dao()
            val streak = dao.getStreak()
            if (streak != null && streak.monthlyDarkRoomBypassTokensLeft > 0) {
                dao.upsertStreak(streak.copy(monthlyDarkRoomBypassTokensLeft = streak.monthlyDarkRoomBypassTokensLeft - 1))
                tokensRemaining = streak.monthlyDarkRoomBypassTokensLeft - 1
            }
        }
        val newTarget = watchdogManager?.activateDarkRoomSquatMode() ?: (targetReps * 3 / 2)
        targetReps = newTarget
        currentReps = 0
        torchActive = false
        setScreenBrightness(-1.0f)
        releaseCameraAndMl()
    }

    // Completes activity with RESULT_OK once target reps are verified
    private fun checkGoalCompletion() {
        if (!completedOnce && currentReps >= targetReps) {
            completedOnce = true
            releaseCameraAndMl()
            val resultIntent = Intent().apply {
                putExtra(EXTRA_VERIFIED_REPS, currentReps)
                putExtra(EXTRA_IS_SQUATS, verifyMode == VerifyMode.DARK_ROOM_GYRO_SQUAT)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    // Closes activity with RESULT_CANCELED when aborted before finishing reps
    private fun finishCancelled() {
        releaseCameraAndMl()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    // Unbinds CameraX and closes MediaPipe interpreter to guarantee zero idle ML RAM
    private fun releaseCameraAndMl() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        runCatching { poseLandmarker?.close() }
        poseLandmarker = null
        cameraExecutor?.shutdownNow()
        cameraExecutor = null
    }

    // Rotates camera frame bitmap to upright portrait orientation
    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // Adjusts window screen brightness for the <5 lux pure-white screen torch
    private fun setScreenBrightness(brightness: Float) {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
    }

    // Checks whether runtime CAMERA permission is already granted
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_TARGET_REPS = "extra_target_reps"
        const val EXTRA_STRICT_MODE = "extra_strict_mode"
        const val EXTRA_VERIFIED_REPS = "extra_verified_reps"
        const val EXTRA_IS_SQUATS = "extra_is_squats"
    }
}
