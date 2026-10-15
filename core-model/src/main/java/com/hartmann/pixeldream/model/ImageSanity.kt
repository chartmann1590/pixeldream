package com.hartmann.pixeldream.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Cheap, framework-free check for a diffusion result that is not a picture at
 * all: a flat/blank frame (a failed decode) or pure high-frequency noise (a run
 * that never converged). A real image, even a rough one, has neighbouring
 * pixels that mostly agree with each other, so the mean neighbour difference
 * is well below the overall spread of the image. White noise puts the two
 * close to equal.
 *
 * Measured on reference images: photos and screenshots score 0.07-0.40,
 * the app's own rough 256 px outputs 0.14-0.38, uniform random noise 1.15.
 */
object ImageSanity {
    /** Mean |neighbour difference| / standard deviation at or above this reads as noise. */
    const val NOISE_RATIO = 0.75

    /** A standard deviation under this (0-255 grey) reads as a blank frame. */
    const val FLAT_STD = 2.0

    enum class Verdict { OK, FLAT, NOISE }

    data class Stats(val std: Double, val neighbourDiff: Double) {
        val ratio: Double get() = if (std <= 0.0) 0.0 else neighbourDiff / std
        val verdict: Verdict
            get() = when {
                std < FLAT_STD -> Verdict.FLAT
                ratio >= NOISE_RATIO -> Verdict.NOISE
                else -> Verdict.OK
            }
    }

    /**
     * @param argb packed ARGB pixels, row-major, width * height long.
     * Samples at most ~128x128 pixels with a fixed stride (no averaging, which
     * would hide noise), converts to grey, and compares each pixel to its right
     * and lower neighbour in the sampled grid.
     */
    fun stats(argb: IntArray, width: Int, height: Int): Stats {
        if (width <= 1 || height <= 1 || argb.size < width * height) return Stats(0.0, 0.0)
        val sx = maxOf(1, width / 128)
        val sy = maxOf(1, height / 128)
        val cols = (width + sx - 1) / sx
        val rows = (height + sy - 1) / sy
        val grey = DoubleArray(cols * rows)
        var sum = 0.0
        var sumSq = 0.0
        var r = 0
        var y = 0
        while (y < height) {
            var c = 0
            var x = 0
            while (x < width) {
                val p = argb[y * width + x]
                val g = 0.299 * ((p shr 16) and 0xff) + 0.587 * ((p shr 8) and 0xff) + 0.114 * (p and 0xff)
                grey[r * cols + c] = g
                sum += g
                sumSq += g * g
                c++
                x += sx
            }
            r++
            y += sy
        }
        val n = (cols * rows).toDouble()
        val mean = sum / n
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        val std = sqrt(variance)

        var diff = 0.0
        var count = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val v = grey[row * cols + col]
                if (col + 1 < cols) { diff += abs(v - grey[row * cols + col + 1]); count++ }
                if (row + 1 < rows) { diff += abs(v - grey[(row + 1) * cols + col]); count++ }
            }
        }
        val neighbour = if (count == 0) 0.0 else diff / count
        return Stats(std, neighbour)
    }

    fun looksDegenerate(argb: IntArray, width: Int, height: Int): Boolean =
        stats(argb, width, height).verdict != Verdict.OK
}
