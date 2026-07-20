package com.hartmann.pixeldream.feedback

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Collects generic, non-PII diagnostics for inclusion in a user-submitted bug
 * report. Intentionally never reads tokens, BuildConfig secret fields, contacts,
 * SMS, location, photos, accounts, or any data from local.properties.
 */
object DiagnosticsHelper {

    suspend fun collect(context: Context): String = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val pkgInfo = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "unknown"
        val versionCode = pkgInfo?.longVersionCode?.takeIf { it > 0 } ?: pkgInfo?.versionCode?.toLong() ?: -1L

        val appName = pm.getApplicationLabel(context.applicationInfo).toString()
        val device = Build.DEVICE ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val brand = Build.BRAND ?: "unknown"
        val androidRelease = Build.VERSION.RELEASE ?: "unknown"
        val apiLevel = Build.VERSION.SDK_INT

        val locale = Locale.getDefault().toString().ifBlank { "unknown" }
        val timeZoneId = TimeZone.getDefault().id ?: "unknown"

        val (storageFree, storageTotal) = storageStats()
        val (memFree, memTotal) = memoryStats()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        buildString {
            appendLine("## Diagnostics")
            appendLine()
            appendLine("- App: $appName")
            appendLine("- Package: ${context.packageName}")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Device: $brand $device ($model)")
            appendLine("- Manufacturer: $manufacturer")
            appendLine("- Android: $androidRelease / API $apiLevel")
            appendLine("- Locale: $locale")
            appendLine("- Time Zone: $timeZoneId")
            appendLine("- Storage Free/Total: $storageFree / $storageTotal")
            appendLine("- Memory Free/Total: $memFree / $memTotal")
            appendLine("- Report time (UTC): $timestamp")
        }
    }

    private fun storageStats(): Pair<String, String> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val free = stat.availableBytes
            val total = stat.totalBytes
            formatBytes(free) to formatBytes(total)
        }.getOrDefault("unknown" to "unknown")
    }

    private fun memoryStats(): Pair<String, String> {
        return runCatching {
            val rt = Runtime.getRuntime()
            val free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
            formatBytes(free) to formatBytes(rt.maxMemory())
        }.getOrDefault("unknown" to "unknown")
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
