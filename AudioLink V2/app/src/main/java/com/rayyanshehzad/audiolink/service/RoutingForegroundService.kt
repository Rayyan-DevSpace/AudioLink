package com.rayyanshehzad.audiolink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rayyanshehzad.audiolink.MainActivity
import com.rayyanshehzad.audiolink.R
import com.rayyanshehzad.audiolink.appscope.ForegroundAppWatcher
import com.rayyanshehzad.audiolink.audio.AudioRoutingManager
import com.rayyanshehzad.audiolink.audio.RoutingResult
import com.rayyanshehzad.audiolink.data.RoutingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The persistent enforcement loop described in architecture §3.3: re-applies the
 * output/input routing policy every REASSERT_INTERVAL_MS so the OS or the target
 * call app can't silently revert Bluetooth back to handling both directions.
 *
 * Edge cases this handles explicitly, rather than silently failing or crashing:
 * - a routed device (Bluetooth earbuds / USB mic) is unplugged or disconnects
 *   mid-call → detected via AudioDeviceCallback, notification updated, loop keeps
 *   running and re-applies automatically once the device reappears
 * - a required permission (RECORD_AUDIO / BLUETOOTH_CONNECT) isn't granted →
 *   AudioRoutingManager returns RoutingResult.PermissionDenied, notification
 *   reflects it instead of the service throwing
 * - Bluetooth is off / no communication-capable device present →
 *   RoutingResult.DeviceUnavailable, same treatment
 * - any unexpected exception in the enforcement pass → caught locally so one bad
 *   cycle doesn't kill the whole service; the next cycle just tries again
 */
class RoutingForegroundService : Service() {

    private lateinit var routingManager: AudioRoutingManager
    private lateinit var repository: RoutingRepository
    private lateinit var appWatcher: ForegroundAppWatcher
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var reassertRunnable: Runnable? = null

    /** Human-readable status shown in the notification — updated on every enforcement pass. */
    private var statusText = "Split routing active — Bluetooth out / USB mic in"

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            scope.launch { handleDevicesRemoved(removedDevices) }
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // A device reappearing (e.g. earbuds reconnect) will simply be picked up
            // by the next scheduled reassert pass — nothing to do here beyond that.
        }
    }

    override fun onCreate() {
        super.onCreate()
        routingManager = AudioRoutingManager(this)
        repository = RoutingRepository(this)
        appWatcher = ForegroundAppWatcher(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startReassertLoop()
        return START_STICKY
    }

    private fun startReassertLoop() {
        reassertRunnable?.let { handler.removeCallbacks(it) }
        reassertRunnable = object : Runnable {
            override fun run() {
                scope.launch { safeApplyPolicy() }
                handler.postDelayed(this, REASSERT_INTERVAL_MS)
            }
        }
        handler.post(reassertRunnable!!)
    }

    /** Wraps the real enforcement pass so one failure never takes the whole service down. */
    private suspend fun safeApplyPolicy() {
        try {
            applyPolicyIfEligible()
        } catch (e: Exception) {
            Log.w(TAG, "Routing pass failed, will retry next cycle", e)
            updateStatus("Routing hit a snag — retrying…")
        }
    }

    private suspend fun applyPolicyIfEligible() {
        val splitOn = repository.splitRoutingOn.first()
        if (!splitOn) return

        val allApps = repository.allApps.first()
        if (!allApps) {
            val selected = repository.selectedApps.first()
            val foreground = appWatcher.currentForegroundPackage()
            if (foreground == null || foreground !in selected) return
        }

        // Only enforce while a real communication-mode session is active, so we
        // don't hijack routing outside of calls.
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            updateStatus("Split routing armed — waiting for a call")
            return
        }

        val outputDevices = routingManager.listOutputDevices()
        val inputDevices = routingManager.listInputDevices()

        val outputDevice = outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val inputDevice = inputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }

        if (outputDevice == null && !routingManager.hasBluetoothOutput()) {
            updateStatus("No Bluetooth output connected — output routing paused")
        } else {
            outputDevice?.let {
                when (val result = routingManager.applyOutputDevice(it.id)) {
                    is RoutingResult.PermissionDenied -> updateStatus("Bluetooth permission needed — open AudioLink to grant it")
                    is RoutingResult.DeviceUnavailable -> updateStatus("Output device disconnected — reconnect to resume")
                    is RoutingResult.Failed -> updateStatus("Output routing issue: ${result.message}")
                    is RoutingResult.Success -> { /* status reset below */ }
                }
            }
        }

        if (inputDevice == null) {
            updateStatus("No external mic detected — using default input")
        } else {
            when (val result = routingManager.applyInputBias(inputDevice.id)) {
                is RoutingResult.PermissionDenied -> updateStatus("Microphone permission needed — open AudioLink to grant it")
                is RoutingResult.DeviceUnavailable -> updateStatus("Input device disconnected — reconnect to resume")
                is RoutingResult.Failed -> updateStatus("Input routing issue: ${result.message}")
                is RoutingResult.Success -> {
                    if (outputDevice != null) {
                        updateStatus("Split routing active — Bluetooth out / ${inputDevice.name} in")
                    }
                }
            }
        }
    }

    private suspend fun handleDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
        val outputName = repository.outputDeviceName.first()
        val inputName = repository.inputDeviceName.first()
        val removedNames = removed.mapNotNull { it.productName?.toString() }
        if (outputName != null && removedNames.any { it == outputName }) {
            updateStatus("Output device disconnected — waiting for it to reconnect")
        }
        if (inputName != null && removedNames.any { it == inputName }) {
            updateStatus("Input device disconnected — waiting for it to reconnect")
        }
        // The next reassert cycle will simply fail to find these devices and
        // re-report status; nothing further needs to happen here.
    }

    private fun updateStatus(text: String) {
        if (statusText == text) return
        statusText = text
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Split routing status", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        reassertRunnable?.let { handler.removeCallbacks(it) }
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        routingManager.clearCommunicationDevice()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RoutingForegroundService"
        private const val CHANNEL_ID = "audiolink_routing_status"
        private const val NOTIFICATION_ID = 1001
        private const val REASSERT_INTERVAL_MS = 1500L

        fun start(context: Context) {
            val intent = Intent(context, RoutingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoutingForegroundService::class.java))
        }
    }
}
