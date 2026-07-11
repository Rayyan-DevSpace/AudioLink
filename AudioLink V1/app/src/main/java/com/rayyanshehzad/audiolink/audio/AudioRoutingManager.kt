package com.rayyanshehzad.audiolink.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class RoutableDevice(
    val id: String,
    val name: String,
    val type: Int,
    val isOutput: Boolean
)

/**
 * The core "split routing" engine described in the architecture doc §3.2/§3.3:
 * - Output is locked to the chosen device via the public Communication Device API
 *   (Android 12+) or legacy SCO calls on older versions.
 * - Input is biased toward the chosen device using device priority, since Android
 *   does not expose a single guaranteed "force input" switch for another app's
 *   call session (see architecture §1 / §3.2 for the full explanation).
 */
class AudioRoutingManager(context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun listOutputDevices(): List<RoutableDevice> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in RELEVANT_OUTPUT_TYPES }
            .map { it.toRoutableDevice(isOutput = true) }

    fun listInputDevices(): List<RoutableDevice> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in RELEVANT_INPUT_TYPES }
            .map { it.toRoutableDevice(isOutput = false) }

    /** Locks call output to the given device id. Reliable, native API (architecture §1). */
    fun applyOutputDevice(deviceId: String) {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id.toString() == deviceId } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.setCommunicationDevice(device)
        } else if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
        }
    }

    /**
     * Biases call input toward the given device id. Best-effort — see architecture
     * §1/§3.2 for why this cannot be 100% guaranteed across every OEM.
     */
    fun applyInputBias(deviceId: String) {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id.toString() == deviceId } ?: return

        if (device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            // Turning SCO off for non-Bluetooth targets lets the OS fall back to the
            // next-priority input, which is typically the wired/USB mic if present.
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }
    }

    fun setCommunicationMode(enabled: Boolean) {
        audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
    }

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
