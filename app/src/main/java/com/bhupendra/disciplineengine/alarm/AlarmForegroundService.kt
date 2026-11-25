package com.bhupendra.disciplineengine.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.bhupendra.disciplineengine.R
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.RpEventEntity
import com.bhupendra.disciplineengine.domain.RpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// Foreground alarm service enforcing volume guard, speaker routing, and 3x3 burst cycle
class AlarmForegroundService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var volumeGuardJob: Job? = null
    private var burstLoopJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var sensorManager: SensorManager? = null
    private lateinit var audioManager: AudioManager
    private lateinit var store: DirectBootAlarmStore
    private val burstMachine = BurstCycleStateMachine()

    // Initializes audio, sensor, and Direct Boot store references
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        store = DirectBootAlarmStore(this)
        ensureNotificationChannel()
    }

    // Starts or stops the kill-proof alarm and full-screen intent notification
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS_VERIFIED -> {
                dismissCleanly()
                return START_NOT_STICKY
            }
            ACTION_REGISTER_TOUCH -> {
                burstMachine.registerInteraction()
                return START_STICKY
            }
        }

        store.setRingingActive(true)
        val notification = buildFullScreenNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        launchVerifyUi()
        startMotionMonitor()
        startBurstAndAudioLoops()
        return START_STICKY
    }

    // Relaunches the alarm UI within 150ms if the user swipes the app from Recents
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (store.isRingingActive()) {
            AlarmScheduler.scheduleImmediateRelaunch(applicationContext)
        }
    }

    // Cleans up audio, vibration, sensors, and coroutines on service shutdown
    override fun onDestroy() {
        stopAudioAndVibration()
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    // Returns null because this foreground service does not support binding
    override fun onBind(intent: Intent?): IBinder? = null

    // Detects physical phone movement to differentiate active user from empty room
    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
            if (abs(magnitude - 9.81f) > 0.4f) {
                burstMachine.registerInteraction()
            }
        }
    }

    // Unused SensorEventListener callback
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // Runs the 400ms alarm volume lock and the 3x3 smart burst cycle timer
    private fun startBurstAndAudioLoops() {
        if (volumeGuardJob?.isActive == true) return
        burstMachine.start(System.currentTimeMillis())

        volumeGuardJob = serviceScope.launch {
            while (isActive) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < maxVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                }
                delay(400L)
            }
        }

        burstLoopJob = serviceScope.launch {
            var lastPhase: BurstPhase? = null
            while (isActive) {
                val snap = burstMachine.tick(System.currentTimeMillis())
                _burstState.value = snap

                if (snap.phase != lastPhase) {
                    when (snap.phase) {
                        BurstPhase.RINGING -> startAudioAndVibration()
                        BurstPhase.PAUSED -> stopAudioAndVibration()
                        BurstPhase.ABANDONED_TIMEOUT -> {
                            handleAbandonedAlarmPenalty()
                            break
                        }
                        BurstPhase.COMPLETED_DISMISSED -> break
                    }
                    lastPhase = snap.phase
                }
                delay(1000L)
            }
        }
    }

    // Forces alarm audio through the built-in phone speaker and starts vibration
    private fun startAudioAndVibration() {
        if (mediaPlayer != null || toneGenerator != null) return
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                findBuiltInSpeaker()?.let { setPreferredDevice(it) }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
                it.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    // Stops alarm playback and vibration during burst pauses or dismissal
    private fun stopAudioAndVibration() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        toneGenerator?.runCatching {
            stopTone()
            release()
        }
        toneGenerator = null

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
    }

    // Finds the hardware built-in speaker to bypass connected earphones
    private fun findBuiltInSpeaker(): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    // Registers accelerometer listener to detect if user physically moved the phone
    private fun startMotionMonitor() {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    // Applies -35 RP and locks Reels Time Bank to 0 after 15 min empty-room timeout
    private suspend fun handleAbandonedAlarmPenalty() {
        store.setRingingActive(false)
        stopAudioAndVibration()
        runCatching {
            val dao = DisciplineDatabase.get(applicationContext).dao()
            dao.addRpEvent(
                RpEventEntity(
                    timestamp = System.currentTimeMillis(),
                    source = RpSource.PENALTY.name,
                    rpDelta = RpSource.PENALTY.rpDelta
                )
            )
            val streak = dao.getStreak()
            if (streak != null) {
                dao.upsertStreak(
                    streak.copy(
                        totalRp = dao.totalRp().coerceAtLeast(0L),
                        reelsTimeBankMinutes = 0
                    )
                )
            }
        }
        rescheduleNextDayIfEnabled()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Cleanly stops the alarm after verified exercise or explicit emergency bypass
    private fun dismissCleanly() {
        store.setRingingActive(false)
        burstMachine.markDismissed(System.currentTimeMillis())
        _burstState.value = burstMachine.snapshot(System.currentTimeMillis())
        stopAudioAndVibration()
        rescheduleNextDayIfEnabled()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Reschedules tomorrow's morning alarm when daily schedule remains enabled
    private fun rescheduleNextDayIfEnabled() {
        if (store.isAlarmEnabled()) {
            AlarmScheduler.scheduleDailyAlarm(
                applicationContext,
                store.alarmHour(),
                store.alarmMinute()
            )
        }
    }

    // Launches the lockscreen verification activity immediately
    private fun launchVerifyUi() {
        val uiIntent = Intent(this, AlarmVerifyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(uiIntent)
    }

    // Builds the full-screen intent notification required for lockscreen wakeups
    private fun buildFullScreenNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmVerifyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            9001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("DISCIPLINE ENGINE ALARM")
            .setContentText("Complete ${store.requiredPushups()} pushups to silence alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    // Creates the high-importance alarm notification channel
    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kill-Proof Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "discipline_alarm_channel"
        const val NOTIFICATION_ID = 3010
        const val ACTION_START_ALARM = "com.bhupendra.disciplineengine.START_ALARM"
        const val ACTION_DISMISS_VERIFIED = "com.bhupendra.disciplineengine.DISMISS_VERIFIED"
        const val ACTION_REGISTER_TOUCH = "com.bhupendra.disciplineengine.REGISTER_TOUCH"

        private val _burstState = MutableStateFlow<BurstCycleSnapshot?>(null)
        val burstState: StateFlow<BurstCycleSnapshot?> = _burstState.asStateFlow()
    }
}
