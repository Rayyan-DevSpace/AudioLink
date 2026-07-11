package com.rayyanshehzad.audiolink.ui.output

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rayyanshehzad.audiolink.audio.RoutableDevice
import com.rayyanshehzad.audiolink.ui.common.SelectableDeviceRow

@Composable
fun OutputScreen(
    devices: List<RoutableDevice>,
    selectedId: String?,
    tonePlaying: Boolean,
    playingDeviceName: String,
    onBack: () -> Unit,
    onSelect: (RoutableDevice) -> Unit,
    onToggleTone: () -> Unit,
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
        Text("Output device", style = MaterialTheme.typography.headlineSmall)
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
            Text("Test tone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleTone,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (tonePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (tonePlaying) "Stop test tone" else "Play test tone",
                        tint = MaterialTheme.colorScheme.background
                    )
                }
                Spacer(modifier = Modifier.padding(start = 12.dp))
                Text(
                    text = if (tonePlaying) "Playing sample tone through $playingDeviceName…"
                    else "Play a sample tone to confirm it's coming through your selected device",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
