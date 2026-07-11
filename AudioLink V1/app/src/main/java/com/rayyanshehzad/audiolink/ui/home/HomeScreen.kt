package com.rayyanshehzad.audiolink.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.unit.dp
import com.rayyanshehzad.audiolink.ui.common.CollapsedRow
import com.rayyanshehzad.audiolink.ui.common.ToggleRow
import com.rayyanshehzad.audiolink.ui.theme.ThemeMode

@Composable
fun HomeScreen(
    outputDeviceName: String,
    inputDeviceName: String,
    appsScopeSummary: String,
    routingActive: Boolean,
    splitRoutingOn: Boolean,
    themeMode: ThemeMode,
    onOpenOutput: () -> Unit,
    onOpenInput: () -> Unit,
    onOpenApps: () -> Unit,
    onSplitRoutingToggle: (Boolean) -> Unit,
    onThemeToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AudioLink", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            if (routingActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Routing active", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Call routing", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(20.dp))

        CollapsedRow(
            icon = { Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            label = "Output · hearing",
            value = outputDeviceName,
            onClick = onOpenOutput
        )
        Spacer(modifier = Modifier.height(10.dp))

        CollapsedRow(
            icon = { Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            label = "Input · speaking",
            value = inputDeviceName,
            onClick = onOpenInput
        )
        Spacer(modifier = Modifier.height(10.dp))

        CollapsedRow(
            icon = { Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            label = "Applies to",
            value = appsScopeSummary,
            onClick = onOpenApps
        )

        Spacer(modifier = Modifier.height(24.dp))

        ToggleRow(
            label = "Split routing",
            checked = splitRoutingOn,
            onCheckedChange = onSplitRoutingToggle
        )

        ToggleRow(
            label = "Dark theme",
            helper = "Off follows your system setting",
            checked = themeMode == ThemeMode.DARK,
            onCheckedChange = onThemeToggle
        )
    }
}
