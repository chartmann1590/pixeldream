package com.hartmann.pixeldream.requirements

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Device system requirements for PixelDream.
 *
 * PixelDream generates images with AI models that run entirely on the device,
 * so the phone needs enough memory, storage, and CPU headroom. [collectSpecs]
 * reads the live device values, while [evaluate] is a pure function over
 * [DeviceSpecs] so the pass/fail logic is unit-testable without the Android
 * framework.
 */
data class DeviceSpecs(
    val totalRamBytes: Long,
    val freeStorageBytes: Long,
    val cpuCores: Int,
    val is64Bit: Boolean,
    val androidApi: Int,
)

data class RequirementCheck(
    val label: String,
    val required: String,
    val actual: String,
    val passed: Boolean,
)

data class RequirementReport(val checks: List<RequirementCheck>) {
    val meetsMinimum: Boolean get() = checks.all { it.passed }
}

object DeviceRequirements {
    const val MIN_RAM_BYTES = 6L * 1024 * 1024 * 1024
    const val MIN_FREE_STORAGE_BYTES = 5L * 1024 * 1024 * 1024
    const val MIN_CPU_CORES = 8
    const val MIN_API = 29

    fun collectSpecs(context: Context): DeviceSpecs {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val freeStorageBytes = StatFs(Environment.getDataDirectory().path).availableBytes
        return DeviceSpecs(
            totalRamBytes = memoryInfo.totalMem,
            freeStorageBytes = freeStorageBytes,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            androidApi = Build.VERSION.SDK_INT,
        )
    }

    fun evaluate(specs: DeviceSpecs): RequirementReport = RequirementReport(
        listOf(
            RequirementCheck(
                label = "Android version",
                required = "Android 10 (API 29) or higher",
                actual = apiLabel(specs.androidApi),
                passed = specs.androidApi >= MIN_API,
            ),
            RequirementCheck(
                label = "RAM",
                required = "6 GB RAM",
                actual = "${formatBytes(specs.totalRamBytes)} RAM",
                passed = specs.totalRamBytes >= MIN_RAM_BYTES,
            ),
            RequirementCheck(
                label = "Processor",
                required = "64-bit ARM (arm64-v8a)",
                actual = if (specs.is64Bit) "64-bit ARM" else "32-bit ARM",
                passed = specs.is64Bit,
            ),
            RequirementCheck(
                label = "CPU cores",
                required = "8 (octa-core) or more",
                actual = "${specs.cpuCores} cores",
                passed = specs.cpuCores >= MIN_CPU_CORES,
            ),
            RequirementCheck(
                label = "Free storage",
                required = "5 GB free storage",
                actual = "${formatBytes(specs.freeStorageBytes)} free storage",
                passed = specs.freeStorageBytes >= MIN_FREE_STORAGE_BYTES,
            ),
        ),
    )

    fun evaluateDevice(context: Context): RequirementReport = evaluate(collectSpecs(context))

    fun formatBytes(bytes: Long): String = "%.1f GB".format(bytes / 1_073_741_824.0)

    private fun apiLabel(api: Int): String = when (api) {
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36 -> "Android 16"
        else -> "Android (API $api)"
    }
}
