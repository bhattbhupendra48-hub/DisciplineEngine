package com.bhupendra.disciplineengine.audio

import android.media.AudioManager
import android.media.ToneGenerator

// Zero-asset singleton RPG audio cue player for S5 grind feedback
object GameSfxPlayer {

    private var toneGenerator: ToneGenerator? = null

    // Plays an uplifting confirmation beep on habit completion or RP gain
    fun playTaskCompleteChime() {
        playTone(ToneGenerator.TONE_PROP_ACK, 140)
    }

    // Plays a short warning alert on form error or penalty
    fun playWarningBuzz() {
        playTone(ToneGenerator.TONE_PROP_NACK, 180)
    }

    // Reuses a single ToneGenerator instance to prevent native audio handle leaks
    @Synchronized
    private fun playTone(toneType: Int, durationMs: Int) {
        runCatching {
            val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75).also {
                toneGenerator = it
            }
            tg.startTone(toneType, durationMs)
        }
    }
}
