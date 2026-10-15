package com.hartmann.pixeldream.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/** The "Use GPU (faster)" setting. */
enum class GpuMode { AUTO, ON, OFF }

/** What Settings shows about the GPU, in plain words. */
data class GpuStatus(
    val mode: GpuMode,
    /** Why the GPU is not used on this phone, or null when nothing is holding it back. */
    val blockedReason: String?,
    /** True once a GPU image has come out clean on this phone. */
    val verified: Boolean,
    /** "GPU" or "CPU" for the last finished image, or null before the first one. */
    val lastBackend: String?,
    val lastGpuSeconds: Double?,
    val lastCpuSeconds: Double?,
)

/**
 * Remembers, per phone, whether the GPU (Vulkan) backend can be trusted.
 *
 * - AUTO uses the GPU only on chips where it was measured to be faster than the CPU
 *   and to give clean images ([AUTO_GPU_SOCS]), on phones that pass the performance
 *   check and have not had a GPU failure. ON tries it on any phone (and clears a
 *   remembered failure). OFF never touches it.
 * - In AUTO, a GPU image that took longer than the last CPU image of the same size
 *   and step count switches the phone back to the CPU.
 * - A failure (the model would not load on the GPU, an error while generating, or a
 *   noise image) is remembered for this phone and build of Android, so an OS update
 *   with a newer GPU driver gets one fresh try.
 * - Crash guard: a flag is written synchronously before every GPU load or run and
 *   cleared afterwards. If the app starts and finds the flag still set, the process
 *   died during GPU work (a GPU driver crash cannot be caught). One such death blocks
 *   an unproven GPU; once the GPU has produced a good image, two in a row are needed,
 *   so swiping the app away mid-image does not switch it off.
 */
class GpuBackendStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(): GpuMode = runCatching { GpuMode.valueOf(prefs.getString(KEY_MODE, null) ?: GpuMode.AUTO.name) }
        .getOrDefault(GpuMode.AUTO)

    fun setMode(mode: GpuMode) {
        val editor = prefs.edit().putString(KEY_MODE, mode.name)
        // Choosing "On" is an explicit request to try again.
        if (mode == GpuMode.ON) editor.remove(KEY_BLOCKED_REASON).remove(KEY_BLOCKED_BUILD).putInt(KEY_DEATHS, 0)
        editor.apply()
    }

    fun blockedReason(): String? {
        val reason = prefs.getString(KEY_BLOCKED_REASON, null) ?: return null
        return if (prefs.getString(KEY_BLOCKED_BUILD, null) == Build.FINGERPRINT) reason else null
    }

    fun shouldTryGpu(tier: DeviceTier, socModel: String = currentSoc()): Boolean = when (mode()) {
        GpuMode.OFF -> false
        GpuMode.ON -> blockedReason() == null
        GpuMode.AUTO -> autoAllowed(tier, socModel) && blockedReason() == null
    }

    /** Call once per process before any GPU work. Returns true if a GPU crash was detected. */
    fun recoverFromCrash(): Boolean {
        if (!prefs.getBoolean(KEY_ATTEMPT, false)) return false
        val deaths = prefs.getInt(KEY_DEATHS, 0) + 1
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        val editor = prefs.edit().putBoolean(KEY_ATTEMPT, false).putInt(KEY_DEATHS, deaths)
        val block = !verified || deaths >= 2
        if (block) {
            editor.putString(KEY_BLOCKED_REASON, "The app closed while the GPU was working, so PixelDream switched back to the CPU.")
                .putString(KEY_BLOCKED_BUILD, Build.FINGERPRINT)
                .putBoolean(KEY_VERIFIED, false)
        }
        editor.commit()
        Log.w(TAG, "Process ended during GPU work (deaths=$deaths, verified=$verified, blocked=$block)")
        return block
    }

    /** Written with commit() so the flag is on disk before native GPU code runs. */
    fun beginGpuAttempt() {
        prefs.edit().putBoolean(KEY_ATTEMPT, true).commit()
    }

    fun endGpuAttempt() {
        prefs.edit().putBoolean(KEY_ATTEMPT, false).commit()
    }

    fun recordFailure(reason: String) {
        Log.w(TAG, "GPU disabled on this phone: $reason")
        prefs.edit()
            .putBoolean(KEY_ATTEMPT, false)
            .putString(KEY_BLOCKED_REASON, reason)
            .putString(KEY_BLOCKED_BUILD, Build.FINGERPRINT)
            .putBoolean(KEY_VERIFIED, false)
            .commit()
    }

    fun recordRun(onGpu: Boolean, seconds: Double, width: Int, steps: Int) {
        val shape = "${width}_$steps"
        val editor = prefs.edit()
            .putString(KEY_LAST_BACKEND, if (onGpu) "GPU" else "CPU")
            .putFloat(if (onGpu) KEY_LAST_GPU_SECONDS else KEY_LAST_CPU_SECONDS, seconds.toFloat())
            .putFloat((if (onGpu) KEY_GPU_SECONDS_FOR else KEY_CPU_SECONDS_FOR) + shape, seconds.toFloat())
        if (onGpu) editor.putBoolean(KEY_VERIFIED, true).putInt(KEY_DEATHS, 0)
        editor.apply()
        if (onGpu && mode() == GpuMode.AUTO) {
            val cpu = prefs.getFloat(KEY_CPU_SECONDS_FOR + shape, -1f)
            if (cpu > 0f && seconds > cpu * SLOWER_MARGIN) {
                recordFailure(
                    "The GPU was slower than the CPU on this phone (%.0f s vs %.0f s), so PixelDream uses the CPU.".format(seconds, cpu),
                )
            }
        }
    }

    fun status(): GpuStatus = GpuStatus(
        mode = mode(),
        blockedReason = blockedReason(),
        verified = prefs.getBoolean(KEY_VERIFIED, false),
        lastBackend = prefs.getString(KEY_LAST_BACKEND, null),
        lastGpuSeconds = prefs.getFloat(KEY_LAST_GPU_SECONDS, -1f).takeIf { it > 0f }?.toDouble(),
        lastCpuSeconds = prefs.getFloat(KEY_LAST_CPU_SECONDS, -1f).takeIf { it > 0f }?.toDouble(),
    )

    companion object {
        private const val TAG = "PixelDreamGpu"
        private const val FILE = "gpu_backend"
        private const val KEY_MODE = "mode"
        private const val KEY_BLOCKED_REASON = "blocked_reason"
        private const val KEY_BLOCKED_BUILD = "blocked_build"
        private const val KEY_ATTEMPT = "attempt_in_progress"
        private const val KEY_DEATHS = "unexplained_deaths"
        private const val KEY_VERIFIED = "verified"
        private const val KEY_LAST_BACKEND = "last_backend"
        private const val KEY_LAST_GPU_SECONDS = "last_gpu_seconds"
        private const val KEY_LAST_CPU_SECONDS = "last_cpu_seconds"
        private const val KEY_GPU_SECONDS_FOR = "gpu_seconds_"
        private const val KEY_CPU_SECONDS_FOR = "cpu_seconds_"
        private const val SLOWER_MARGIN = 1.1f

        /**
         * Chips where the Vulkan backend was measured faster than the CPU with clean
         * images. Auto mode only uses the GPU on these; everywhere else the GPU is
         * opt-in (Settings > Speed > On). Matched case-insensitively against
         * Build.SOC_MODEL. Empty until a chip has been measured.
         */
        val AUTO_GPU_SOCS: Set<String> = emptySet()

        fun autoAllowed(tier: DeviceTier, socModel: String): Boolean =
            tier == DeviceTier.RECOMMENDED && AUTO_GPU_SOCS.any { it.equals(socModel.trim(), ignoreCase = true) }

        fun currentSoc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrDefault("")
        } else ""

        /**
         * Test hook for checking the fallback on a real phone without a broken GPU:
         * `adb shell setprop debug.pixeldream.gpu_fail init` (the GPU load fails) or
         * `noise` (the GPU image is treated as noise). Unset in normal use.
         */
        fun debugFailure(): String? = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, "debug.pixeldream.gpu_fail", "") as String).trim().lowercase()
        }.getOrNull()?.takeIf { it.isNotEmpty() && it != "0" && it != "off" }
    }
}
