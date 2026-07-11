package com.rayyanshehzad.audiolink.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Preview-only mic reader used solely while the Input screen is visible, so the
 * user can visually confirm the selected device is really picking up their voice.
 * This is intentionally separate from any real call's AudioRecord session — it is
 * torn down as soon as the screen closes (architecture §3.6).
 */
class InputSpectrumReader(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun levels(barCount: Int = 24): Flow<List<Float>> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            close(SecurityException("RECORD_AUDIO not granted"))
            return@callbackFlow
        }

        val sampleRate = 44100
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer else 2048

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val buffer = ShortArray(bufferSize / 4)
        var running = true

        recorder.startRecording()

        val thread = Thread {
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) sum += buffer[i] * buffer[i]
                    val rms = sqrt(sum / read)
                    val normalized = (rms / SHORT_MAX_AMPLITUDE).coerceIn(0.0, 1.0).toFloat()
                    val bars = List(barCount) {
                        (normalized * (0.6f + 0.4f * kotlin.random.Random.nextFloat())).coerceIn(0f, 1f)
                    }
                    trySend(bars)
                }
                Thread.sleep(120)
            }
        }
        thread.start()

        awaitClose {
            running = false
            recorder.stop()
            recorder.release()
        }
    }

    companion object {
        private const val SHORT_MAX_AMPLITUDE = 32767.0
    }
}
