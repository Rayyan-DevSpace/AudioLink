package com.rayyanshehzad.audiolink

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayyanshehzad.audiolink.appscope.ForegroundAppWatcher
import com.rayyanshehzad.audiolink.appscope.InstalledApp
import com.rayyanshehzad.audiolink.audio.AudioRoutingManager
import com.rayyanshehzad.audiolink.audio.InputSpectrumReader
import com.rayyanshehzad.audiolink.audio.RoutableDevice
import com.rayyanshehzad.audiolink.audio.SpectrumState
import com.rayyanshehzad.audiolink.audio.TestTonePlayer
import com.rayyanshehzad.audiolink.service.RoutingForegroundService
import com.rayyanshehzad.audiolink.ui.apps.AppsScreen
import com.rayyanshehzad.audiolink.ui.home.HomeScreen
import com.rayyanshehzad.audiolink.ui.input.InputScreen
import com.rayyanshehzad.audiolink.ui.output.OutputScreen
import com.rayyanshehzad.audiolink.ui.theme.AudioLinkTheme
import com.rayyanshehzad.audiolink.ui.theme.ThemeMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class Screen { HOME, OUTPUT, INPUT, APPS }

class MainActivity : ComponentActivity() {

    private lateinit var routingManager: AudioRoutingManager
    private lateinit var tonePlayer: TestTonePlayer
    private lateinit var spectrumReader: InputSpectrumReader
    private lateinit var appWatcher: ForegroundAppWatcher

    private val hasUsageAccessState = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Screens re-read device/mic state on their own each time they're opened. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        routingManager = AudioRoutingManager(this)
        tonePlayer = TestTonePlayer()
        spectrumReader = InputSpectrumReader(this)
        appWatcher = ForegroundAppWatcher(this)

        requestRuntimePermissions()

        val repository = (application as AudioLinkApp).repository

        setContent {
            var screen by remember { mutableStateOf(Screen.HOME) }
            var selectedOutputId by remember { mutableStateOf<String?>(null) }
            var selectedInputId by remember { mutableStateOf<String?>(null) }
            var tonePlaying by remember { mutableStateOf(false) }
            var spectrumState by remember { mutableStateOf<SpectrumState>(SpectrumState.Levels(List(24) { 0f })) }

            val hasUsageAccess by hasUsageAccessState

            val outputDeviceName by repository.outputDeviceName.collectAsStateWithLifecycle(null)
            val inputDeviceName by repository.inputDeviceName.collectAsStateWithLifecycle(null)
            val splitRoutingOn by repository.splitRoutingOn.collectAsStateWithLifecycle(false)
            val allApps by repository.allApps.collectAsStateWithLifecycle(true)
            val selectedApps by repository.selectedApps.collectAsStateWithLifecycle(emptySet())
            val themeMode by repository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)

            // Recomputed each time the relevant screen is opened, so a device that
            // was unplugged/reconnected since the last visit shows up correctly.
            val outputDevices = remember(screen) {
                if (screen == Screen.OUTPUT) routingManager.listOutputDevices() else emptyList()
            }
            val inputDevices = remember(screen) {
                if (screen == Screen.INPUT) routingManager.listInputDevices() else emptyList()
            }
            val installedApps = remember(screen) {
                if (screen == Screen.APPS) appWatcher.listInstalledApps() else emptyList<InstalledApp>()
            }

            val scope = androidx.compose.runtime.rememberCoroutineScope()

            LaunchedEffect(screen) {
                if (screen == Screen.INPUT) {
                    spectrumReader.levels().collectLatest { state -> spectrumState = state }
                }
            }

            AudioLinkTheme(themeMode = themeMode) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        outputDeviceName = outputDeviceName ?: "Not selected",
                        inputDeviceName = inputDeviceName ?: "Not selected",
                        appsScopeSummary = if (allApps) "All apps" else "${selectedApps.size} apps selected",
                        routingActive = splitRoutingOn,
                        splitRoutingOn = splitRoutingOn,
                        themeMode = themeMode,
                        onOpenOutput = { screen = Screen.OUTPUT },
                        onOpenInput = { screen = Screen.INPUT },
                        onOpenApps = { screen = Screen.APPS },
                        onSplitRoutingToggle = { on ->
                            scope.launch { repository.setSplitRoutingOn(on) }
                            if (on) {
                                RoutingForegroundService.start(this@MainActivity)
                                requestIgnoreBatteryOptimizations()
                            } else {
                                RoutingForegroundService.stop(this@MainActivity)
                            }
                        },
                        onThemeToggle = { dark ->
                            scope.launch {
                                repository.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.SYSTEM)
                            }
                        }
                    )

                    Screen.OUTPUT -> OutputScreen(
                        devices = outputDevices,
                        selectedId = selectedOutputId,
                        tonePlaying = tonePlaying,
                        playingDeviceName = outputDevices.firstOrNull { it.id == selectedOutputId }?.name
                            ?: (outputDeviceName ?: "device"),
                        onBack = {
                            tonePlayer.stop(); tonePlaying = false
                            screen = Screen.HOME
                        },
                        onSelect = { device: RoutableDevice ->
                            selectedOutputId = device.id
                            routingManager.applyOutputDevice(device.id)
                            scope.launch { repository.setOutputDevice(device.id, device.name) }
                        },
                        onToggleTone = {
                            tonePlaying = !tonePlaying
                            if (tonePlaying) tonePlayer.play() else tonePlayer.stop()
                        }
                    )

                    Screen.INPUT -> InputScreen(
                        devices = inputDevices,
                        selectedId = selectedInputId,
                        selectedDeviceName = inputDevices.firstOrNull { it.id == selectedInputId }?.name
                            ?: (inputDeviceName ?: "device"),
                        spectrumState = spectrumState,
                        showOemNote = selectedInputId != null &&
                            inputDevices.firstOrNull { it.id == selectedInputId }?.type
                                != android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                        onBack = { screen = Screen.HOME },
                        onSelect = { device: RoutableDevice ->
                            selectedInputId = device.id
                            routingManager.applyInputBias(device.id)
                            scope.launch { repository.setInputDevice(device.id, device.name) }
                        },
                        onRequestMicPermission = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    )

                    Screen.APPS -> AppsScreen(
                        allApps = allApps,
                        installedApps = installedApps,
                        selectedPackages = selectedApps,
                        hasUsageAccess = hasUsageAccess,
                        onBack = { screen = Screen.HOME },
                        onAllAppsChange = { on -> scope.launch { repository.setAllApps(on) } },
                        onToggleApp = { pkg ->
                            scope.launch {
                                val updated = if (pkg in selectedApps) selectedApps - pkg else selectedApps + pkg
                                repository.setSelectedApps(updated)
                            }
                        },
                        onRequestUsageAccess = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just come back from the usage-access or battery
        // optimization Settings screens — refresh state so banners clear.
        hasUsageAccessState.value = appWatcher.hasUsageAccess()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    /** Foreground services doing mic/Bluetooth work are prime targets for battery-optimization
     *  kills; ask once (non-blocking, user can decline) whenever routing is turned on. */
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                // Some OEMs restrict this intent; routing still works, it's just
                // more likely to be killed under aggressive battery management.
            }
        }
    }

    override fun onDestroy() {
        tonePlayer.stop()
        super.onDestroy()
    }
}
