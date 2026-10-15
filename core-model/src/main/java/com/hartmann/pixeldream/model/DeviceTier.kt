package com.hartmann.pixeldream.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

enum class DeviceTier { UNSUPPORTED, LOW, RECOMMENDED }

/**
 * What the detector learned about this phone, in plain numbers, so the UI can
 * explain a tier instead of just naming it.
 */
data class DeviceProfile(
    val tier: DeviceTier,
    val totalRamMb: Int,
    val cpuCores: Int,
    /** Highest advertised CPU clock in MHz, or 0 when the kernel does not expose it. */
    val maxCpuMhz: Int,
    val is64Bit: Boolean,
    val socModel: String,
) {
    /** One short sentence for the person, or null when there is nothing to warn about. */
    val warning: String?
        get() = when (tier) {
            DeviceTier.RECOMMENDED -> null
            DeviceTier.LOW -> buildString {
                append("This phone is below what the image model works best on (")
                append(summary)
                append("). Images will take longer and look rougher, so PixelDream uses its lighter settings here.")
            }
            DeviceTier.UNSUPPORTED -> buildString {
                append("This phone (")
                append(summary)
                append(") is below the minimum PixelDream needs. Generation may fail or produce poor results.")
            }
        }

    val summary: String
        get() = buildString {
            append("%.1f GB RAM".format(totalRamMb / 1024.0))
            append(", $cpuCores cores")
            if (maxCpuMhz > 0) append(", %.1f GHz".format(maxCpuMhz / 1000.0))
            if (socModel.isNotBlank()) append(", $socModel")
        }
}

object DeviceTierDetector {
    private const val RECOMMENDED_RAM_MB = 8_192
    const val MINIMUM_RAM_MB = 6_144
    private const val RECOMMENDED_CORES = 8

    /**
     * Big cores on phones that run Stable Diffusion comfortably on the CPU sit at
     * roughly 2.4 GHz and above (Snapdragon 7 Gen 1 and up, Dimensity 8000 and up,
     * Tensor, recent Exynos). Below that, a 512 px image takes several minutes.
     */
    private const val RECOMMENDED_MAX_MHZ = 2_400

    fun totalRamMb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024 * 1024)).toInt()
    }

    fun tierFor(context: Context): DeviceTier = profileFor(context).tier

    fun profileFor(context: Context): DeviceProfile {
        val ramMb = totalRamMb(context)
        val cores = Runtime.getRuntime().availableProcessors()
        val maxMhz = maxCpuMhz()
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        return DeviceProfile(
            tier = classify(ramMb, cores, maxMhz, is64Bit),
            totalRamMb = ramMb,
            cpuCores = cores,
            maxCpuMhz = maxMhz,
            is64Bit = is64Bit,
            socModel = socModel(),
        )
    }

    /** Pure classification so it can be unit-tested without the framework. */
    fun classify(ramMb: Int, cpuCores: Int, maxCpuMhz: Int, is64Bit: Boolean): DeviceTier = when {
        ramMb < MINIMUM_RAM_MB || !is64Bit -> DeviceTier.UNSUPPORTED
        ramMb < RECOMMENDED_RAM_MB -> DeviceTier.LOW
        cpuCores < RECOMMENDED_CORES -> DeviceTier.LOW
        // An unreadable clock (0) is not held against the phone.
        maxCpuMhz in 1 until RECOMMENDED_MAX_MHZ -> DeviceTier.LOW
        else -> DeviceTier.RECOMMENDED
    }

    /**
     * Highest cpuinfo_max_freq across all cores, in MHz. The sysfs files are world
     * readable on every Android kernel we have seen, but a vendor can hide them, in
     * which case this returns 0 and the tier falls back to RAM and core count.
     */
    fun maxCpuMhz(): Int {
        var best = 0L
        runCatching {
            val cpus = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            cpus?.forEach { cpu ->
                val f = File(cpu, "cpufreq/cpuinfo_max_freq")
                if (f.canRead()) {
                    val khz = f.readText().trim().toLongOrNull() ?: 0L
                    if (khz > best) best = khz
                }
            }
        }
        return (best / 1000).toInt()
    }

    private fun socModel(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrDefault("")
        } else ""
        val cleaned = soc.takeUnless { it.isBlank() || it.equals(Build.UNKNOWN, ignoreCase = true) }
        return cleaned ?: Build.HARDWARE.orEmpty()
    }
}
