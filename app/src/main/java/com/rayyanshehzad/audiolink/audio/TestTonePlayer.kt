package com.rayyanshehzad.audiolink.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Plays a short confirmation tone on the voice-call audio stream, so it follows
 * whatever device is currently set via AudioRoutingManager.applyOutputDevice —
 * this is what lets the Output screen's "test tone" prove which physical device
 * is really receiving the routed audio (architecture §3.5).
 */
class TestTonePlayer {

    private var toneGenerator: ToneGenerator? = null

    fun play() {
        stop()
        toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
    }

    fun stop() {
        toneGenerator?.release()
        toneGenerator = null
    }

    companion object {
        private const val TONE_DURATION_MS = 600
    }
}
