package com.rayyanshehzad.audiolink.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.rayyanshehzad.audiolink.appscope.InstalledApp
import com.rayyanshehzad.audiolink.ui.common.ToggleRow

@Composable
fun AppsScreen(
    allApps: Boolean,
    installedApps: List<InstalledApp>,
    selectedPackages: Set<String>,
    onBack: () -> Unit,
    onAllAppsChange: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
) {
    val listAlpha by animateFloatAsState(if (allApps) 0.45f else 1f, label = "appsListAlpha")

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
        Text("Applies to", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose which apps split routing turns on for automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        ToggleRow(
            label = "All apps",
            helper = "Applies system-wide to any app using call audio",
            checked = allApps,
            onCheckedChange = onAllAppsChange
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Or select specific apps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().alpha(listAlpha)) {
            items(installedApps, key = { it.packageName }) { app ->
                ToggleRow(
                    label = app.label,
                    checked = app.packageName in selectedPackages,
                    onCheckedChange = { if (!allApps) onToggleApp(app.packageName) }
                )
            }
        }
    }
}
