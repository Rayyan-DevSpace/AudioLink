package com.rayyanshehzad.audiolink.appscope

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

data class InstalledApp(val packageName: String, val label: String)

/**
 * Detects which app is currently in the foreground, used only when the user opts
 * out of "All apps" on the Apps screen (architecture §3.7). Requires the special
 * PACKAGE_USAGE_STATS access, which the user grants manually via Settings — this
 * class also exposes a helper to check/launch that grant flow.
 */
class ForegroundAppWatcher(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Returns the package name most recently moved to the foreground, or null. */
    fun currentForegroundPackage(windowMillis: Long = 10_000L): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - windowMillis
        val events = usm.queryEvents(start, end)
        var lastForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }

    /** Lists user-facing installed apps for the multi-select list on the Apps screen. */
    fun listInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
