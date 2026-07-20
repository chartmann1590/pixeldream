package com.hartmann.pixeldream.model

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier { UNSUPPORTED, LOW, RECOMMENDED }

object DeviceTierDetector {
    private const val RECOMMENDED_RAM_MB = 8_192
    const val MINIMUM_RAM_MB = 6_144

    fun totalRamMb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024 * 1024)).toInt()
    }

    fun tierFor(context: Context): DeviceTier {
        val ramMb = totalRamMb(context)
        return when {
            ramMb >= RECOMMENDED_RAM_MB -> DeviceTier.RECOMMENDED
            ramMb >= MINIMUM_RAM_MB -> DeviceTier.LOW
            else -> DeviceTier.UNSUPPORTED
        }
    }
}
