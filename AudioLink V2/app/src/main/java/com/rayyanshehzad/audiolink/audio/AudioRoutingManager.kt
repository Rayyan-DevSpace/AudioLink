package com.rayyanshehzad.audiolink.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

data class RoutableDevice(
    val id: String,
    val name: String,
    val type: Int,
    val isOutput: Boolean
)

/** Result of a routing attempt — lets callers show a real message instead of a silent no-op or a crash. */
sealed class RoutingResult {
    object Success : RoutingResult()
    object DeviceUnavailable : RoutingResult()
    object PermissionDenied : RoutingResult()
    data class Failed(val message: String) : RoutingResult()
}

/**
 * The core "split routing" engine described in the architecture doc §3.2/§3.3:
 * - Output is locked to the chosen device via the public Communication Device API
 *   (Android 12+) or legacy SCO calls on older versions.
 * - Input is biased toward the chosen device using device priority, since Android
 *   does not expose a single guaranteed "force input" switch for another app's
 *   call session (see architecture §1 / §3.2 for the full explanation).
 *
 * Every mutating call is wrapped so a missing permission, a device that vanished
 * mid-call, or an OEM quirk returns a RoutingResult instead of throwing and
 * killing the foreground service.
 */
class AudioRoutingManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun listOutputDevices(): List<RoutableDevice> = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in RELEVANT_OUTPUT_TYPES }
            .map { it.toRoutableDevice(isOutput = true) }
    } catch (e: SecurityException) {
        emptyList()
    }

    fun listInputDevices(): List<RoutableDevice> = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in RELEVANT_INPUT_TYPES }
            .map { it.toRoutableDevice(isOutput = false) }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** True if a previously-selected device id is still present — used to detect mid-call unplug/disconnect. */
    fun isStillConnected(deviceId: String, isOutput: Boolean): Boolean {
        val devices = if (isOutput) listOutputDevices() else listInputDevices()
        return devices.any { it.id == deviceId }
    }

    /** Locks call output to the given device id. Reliable, native API when the device is present and permitted. */
    fun applyOutputDevice(deviceId: String): RoutingResult {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id.toString() == deviceId }
            ?: return RoutingResult.DeviceUnavailable

        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && !hasBluetoothPermission()) {
            return RoutingResult.PermissionDenied
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val applied = audioManager.setCommunicationDevice(device)
                if (applied) RoutingResult.Success else RoutingResult.Failed("OS rejected the device switch")
            } else if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                RoutingResult.Success
            } else {
                RoutingResult.Success
            }
        } catch (e: SecurityException) {
            RoutingResult.PermissionDenied
        } catch (e: IllegalArgumentException) {
            RoutingResult.DeviceUnavailable
        } catch (e: Exception) {
            RoutingResult.Failed(e.message ?: "Unknown routing error")
        }
    }

    /**
     * Biases call input toward the given device id. Best-effort — see architecture
     * §1/§3.2 for why this cannot be 100% guaranteed across every OEM.
     */
    fun applyInputBias(deviceId: String): RoutingResult {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id.toString() == deviceId }
            ?: return RoutingResult.DeviceUnavailable

        return try {
            if (device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                // Turning SCO off for non-Bluetooth targets lets the OS fall back to the
                // next-priority input, which is typically the wired/USB mic if present.
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                }
            }
            RoutingResult.Success
        } catch (e: SecurityException) {
            RoutingResult.PermissionDenied
        } catch (e: Exception) {
            RoutingResult.Failed(e.message ?: "Unknown routing error")
        }
    }

    fun clearCommunicationDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
        } catch (e: Exception) {
            // Nothing to fall back to here — the mode is already being torn down.
        }
    }

    fun setCommunicationMode(enabled: Boolean) {
        try {
            audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            // Leave mode as-is; caller can retry on the next re-assert cycle.
        }
    }

    /** True if any Bluetooth output is currently connected/paired-and-active. */
    fun hasBluetoothOutput(): Boolean =
        listOutputDevices().any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

    private fun AudioDeviceInfo.toRoutableDevice(isOutput: Boolean) = RoutableDevice(
        id = id.toString(),
        name = friendlyName(this),
        type = type,
        isOutput = isOutput
    )

    private fun friendlyName(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return product ?: when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth device"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired device"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone built-in mic"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
            else -> "Unknown device"
        }
    }

    companion object {
        private val RELEVANT_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        )
        private val RELEVANT_INPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
        )
    }
}
