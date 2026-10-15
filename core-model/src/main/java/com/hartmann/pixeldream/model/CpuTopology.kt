package com.hartmann.pixeldream.model

import java.io.File

/**
 * Picks how many CPU threads the image engine uses. Phone CPUs mix fast and slow
 * cores; the engine splits every step evenly across its threads and waits for the
 * slowest one, so running on the efficiency cores too mostly adds waiting. Use
 * only the cores faster than the slowest cluster when there are enough of them.
 */
object CpuTopology {
    private const val MIN_PERFORMANCE_CORES = 4
    private const val MAX_THREADS = 8

    fun imageThreads(): Int = performanceCores()
        ?: Runtime.getRuntime().availableProcessors().coerceIn(4, MAX_THREADS)

    fun performanceCores(): Int? = runCatching {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
            File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                .takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
        }
        performanceCoreCount(freqs)
    }.getOrNull()

    /** Cores whose top clock beats the slowest cluster's, or null when that rule does not apply. */
    fun performanceCoreCount(maxFreqsKhz: List<Long>): Int? {
        if (maxFreqsKhz.size < 2) return null
        val slowest = maxFreqsKhz.minOrNull() ?: return null
        val fast = maxFreqsKhz.count { it > slowest }
        return if (fast >= MIN_PERFORMANCE_CORES) fast.coerceAtMost(MAX_THREADS) else null
    }
}
