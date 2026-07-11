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

/** State of the live meter — lets the UI show a real message instead of dead bars. */
sealed class SpectrumState {
    data class Levels(val bars: List<Float>) : SpectrumState()
    object PermissionDenied : SpectrumState()
    object DeviceUnavailable : SpectrumState()
    data class Error(val message: String) : SpectrumState()
}

/**
 * Preview-only mic reader used solely while the Input screen is visible, so the
 * user can visually confirm the selected device is really picking up their voice.
 * This is intentionally separate from any real call's AudioRecord session — it is
 * torn down as soon as the screen closes (architecture §3.6).
 *
 * Never throws out of the flow — every failure path (no permission, device busy,
 * mic unplugged mid-preview) resolves to a SpectrumState the UI can render.
 */
class InputSpectrumReader(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun levels(barCount: Int = 24): Flow<SpectrumState> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            trySend(SpectrumState.PermissionDenied)
            close()
            return@callbackFlow
        }

        val sampleRate = 44100
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBuffer > 0) minBuffer else 2048

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            trySend(SpectrumState.PermissionDenied)
            close()
            return@callbackFlow
        } catch (e: Exception) {
            trySend(SpectrumState.Error(e.message ?: "Couldn't open microphone"))
            close()
            return@callbackFlow
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            trySend(SpectrumState.DeviceUnavailable)
            recorder.release()
            close()
            return@callbackFlow
        }

        val buffer = ShortArray(bufferSize / 4)
        var running = true

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            trySend(SpectrumState.DeviceUnavailable)
            recorder.release()
            close()
            return@callbackFlow
        }

        val thread = Thread {
            while (running) {
                try {
                    if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        trySend(SpectrumState.DeviceUnavailable)
                        break
                    }
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i] * buffer[i]
                        val rms = sqrt(sum / read)
                        val normalized = (rms / SHORT_MAX_AMPLITUDE).coerceIn(0.0, 1.0).toFloat()
                        val bars = List(barCount) {
                            (normalized * (0.6f + 0.4f * kotlin.random.Random.nextFloat())).coerceIn(0f, 1f)
                        }
                        trySend(SpectrumState.Levels(bars))
                    } else if (read < 0) {
                        // Negative return = AudioRecord error code (device gone, bad state, etc.)
                        trySend(SpectrumState.DeviceUnavailable)
                        break
                    }
                } catch (e: Exception) {
                    trySend(SpectrumState.Error(e.message ?: "Microphone read failed"))
                    break
                }
                Thread.sleep(120)
            }
        }
        thread.start()

        awaitClose {
            running = false
            try {
                recorder.stop()
            } catch (e: Exception) {
                // Already stopped/disconnected — nothing to clean up.
            }
            recorder.release()
        }
    }

    companion object {
        private const val SHORT_MAX_AMPLITUDE = 32767.0
    }
}
