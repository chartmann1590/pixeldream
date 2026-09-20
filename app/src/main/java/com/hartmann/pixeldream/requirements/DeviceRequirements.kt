package com.hartmann.pixeldream.requirements


import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File


/**
 * Device system requirements for PixelDream.
 *
 * PixelDream generates images with AI models that run entirely on the device,
 * so the phone needs enough memory, storage, and CPU headroom. [collectSpecs]
 * reads the live device values, while [evaluate] is a pure function over
 * [DeviceSpecs] so the pass/fail logic is unit-testable without the Android
 * framework.
 *
 * Storage is measured on the volume that holds the downloaded models (the app's
 * external files directory, where ModelStorage keeps them), and bytes already
 * occupied by downloaded models count toward the requirement: the 5 GB covers
 * the one-time ~4.1 GB model download, so a device must not start warning after
 * it has successfully downloaded the models it needs.
 */
data class DeviceSpecs(
    val totalRamBytes: Long,
    val freeStorageBytes: Long,
    val installedModelBytes: Long = 0L,
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
        return DeviceSpecs(
            totalRamBytes = memoryInfo.totalMem,
            freeStorageBytes = freeStorageOnModelsVolume(context),
            installedModelBytes = installedModelBytes(context),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            androidApi = Build.VERSION.SDK_INT,
        )
    }


    /**
     * Free bytes on the volume that holds the downloaded models. Models are stored under the
     * app's external files directory, which can be a different volume than the internal data
     * directory, so the check measures that volume. Never throws: falls back to the data
     * directory, then to 0 (failing the check instead of crashing the caller).
     */
    private fun freeStorageOnModelsVolume(context: Context): Long {
        val modelsPath = context.getExternalFilesDir(null)?.absolutePath
        if (modelsPath != null) {
            runCatching { StatFs(modelsPath).availableBytes }.onSuccess { return it }
        }
        return runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }
            .getOrDefault(0L)
    }


    /**
     * Bytes already occupied by downloaded models. These count toward the storage requirement,
     * which exists to cover the one-time model download.
     */
    private fun installedModelBytes(context: Context): Long {
        val modelsRoot = context.getExternalFilesDir(null)?.let { File(it, "models") } ?: return 0L
        return runCatching {
            modelsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
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
                actual = buildString {
                    append("${formatBytes(specs.freeStorageBytes)} free")
                    if (specs.installedModelBytes > 0L) {
                        append(" (+ ${formatBytes(specs.installedModelBytes)} in downloaded models)")
                    }
                },
                passed = specs.freeStorageBytes + specs.installedModelBytes >= MIN_FREE_STORAGE_BYTES,
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
