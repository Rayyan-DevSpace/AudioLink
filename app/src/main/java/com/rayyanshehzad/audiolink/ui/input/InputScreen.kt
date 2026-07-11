package com.rayyanshehzad.audiolink.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
// import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rayyanshehzad.audiolink.audio.RoutableDevice
import com.rayyanshehzad.audiolink.audio.SpectrumState
import com.rayyanshehzad.audiolink.ui.common.SelectableDeviceRow

@Composable
fun InputScreen(
    devices: List<RoutableDevice>,
    selectedId: String?,
    selectedDeviceName: String,
    spectrumState: SpectrumState,
    showOemNote: Boolean,
    onBack: () -> Unit,
    onSelect: (RoutableDevice) -> Unit,
    onRequestMicPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text("Call routing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Input device", style = MaterialTheme.typography.headlineSmall)

        if (devices.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No input devices detected. Check Bluetooth is on or your mic is plugged in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column {
            devices.forEach { device ->
                SelectableDeviceRow(
                    name = device.name,
                    selected = device.id == selectedId,
                    onClick = { onSelect(device) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Live input level", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Text("via $selectedDeviceName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(10.dp))

            when (spectrumState) {
                is SpectrumState.Levels -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        spectrumState.bars.forEach { level ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((4 + level * 44).dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            ) {}
                        }
                    }
                }
                is SpectrumState.PermissionDenied -> {
                    Text(
                        "Microphone permission is needed to show live levels.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestMicPermission) {
                        Text("Grant microphone access")
                    }
                }
                is SpectrumState.DeviceUnavailable -> {
                    Text(
                        "Couldn't reach this microphone — it may have been unplugged or disconnected. Try selecting it again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                is SpectrumState.Error -> {
                    Text(
                        "Mic preview unavailable right now (${spectrumState.message}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (showOemNote) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Input switching may not be fully supported on this device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
