package com.hartmann.pixeldream.model

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSanityTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun uniformRandomNoiseIsFlaggedAsNoise() {
        val random = Random(7)
        val size = 256
        val pixels = IntArray(size * size) { rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        val stats = ImageSanity.stats(pixels, size, size)
        assertEquals(ImageSanity.Verdict.NOISE, stats.verdict)
        assertTrue(stats.ratio > 1.0)
        assertTrue(ImageSanity.looksDegenerate(pixels, size, size))
    }

    @Test
    fun blankFrameIsFlaggedAsFlat() {
        val size = 256
        val pixels = IntArray(size * size) { rgb(12, 12, 12) }
        assertEquals(ImageSanity.Verdict.FLAT, ImageSanity.stats(pixels, size, size).verdict)
    }

    @Test
    fun smoothGradientWithEdgesPassesAsAPicture() {
        val size = 512
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            // A horizontal gradient with a dark rectangle in the middle, roughly a picture's statistics.
            val inside = x in 160..350 && y in 120..400
            val v = if (inside) 30 else (x * 255 / size)
            rgb(v, (v * 3 / 4), 255 - v)
        }
        val stats = ImageSanity.stats(pixels, size, size)
        assertEquals(ImageSanity.Verdict.OK, stats.verdict)
        assertTrue(stats.ratio < 0.5)
        assertFalse(ImageSanity.looksDegenerate(pixels, size, size))
    }

    @Test
    fun tinyOrMalformedInputIsNotFlagged() {
        assertFalse(ImageSanity.looksDegenerate(IntArray(0), 0, 0))
        // Wrong buffer size reports no stats instead of throwing. (std 0 reads as FLAT by design.)
        assertEquals(0.0, ImageSanity.stats(IntArray(3), 4, 4).std, 0.0)
    }
}
