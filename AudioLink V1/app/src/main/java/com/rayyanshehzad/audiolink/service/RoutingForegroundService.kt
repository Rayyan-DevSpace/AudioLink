package com.rayyanshehzad.audiolink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.rayyanshehzad.audiolink.MainActivity
import com.rayyanshehzad.audiolink.R
import com.rayyanshehzad.audiolink.appscope.ForegroundAppWatcher
import com.rayyanshehzad.audiolink.audio.AudioRoutingManager
import com.rayyanshehzad.audiolink.data.RoutingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The persistent enforcement loop described in architecture §3.3: re-applies the
 * output/input routing policy every REASSERT_INTERVAL_MS so that the OS or the
 * target call app can't silently revert Bluetooth back to handling both directions.
 * Also gates itself on the App Scope engine (§3.7) when "All apps" is off.
 */
class RoutingForegroundService : Service() {

    private lateinit var routingManager: AudioRoutingManager
    private lateinit var repository: RoutingRepository
    private lateinit var appWatcher: ForegroundAppWatcher
    private lateinit var audioManager: AudioManager

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var reassertRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        routingManager = AudioRoutingManager(this)
        repository = RoutingRepository(this)
        appWatcher = ForegroundAppWatcher(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
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
                scope.launch { applyPolicyIfEligible() }
                handler.postDelayed(this, REASSERT_INTERVAL_MS)
            }
        }
        handler.post(reassertRunnable!!)
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
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) return

        val outputDevices = routingManager.listOutputDevices()
        val inputDevices = routingManager.listInputDevices()

        outputDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?.let { routingManager.applyOutputDevice(it.id) }

        inputDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }?.let { routingManager.applyInputBias(it.id) }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Split routing active — Bluetooth out / USB mic in")
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        reassertRunnable?.let { handler.removeCallbacks(it) }
        routingManager.clearCommunicationDevice()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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
