package com.hartmann.pixeldream.diffusion

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin lifecycle-safe Kotlin wrapper around stable-diffusion.cpp. */
class StableDiffusion {
    private fun interface NativeProgressListener {
        fun onProgress(step: Int, total: Int)
    }

    companion object {
        init {
            System.loadLibrary("pixeldream_diffusion")
        }
    }

    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    suspend fun load(modelPath: String, threads: Int): Boolean = withContext(Dispatchers.Default) {
        close()
        handle = loadModelNative(modelPath, threads)
        handle != 0L
    }

    suspend fun generate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        onProgress: (step: Int, total: Int) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        check(handle != 0L) { "Stable Diffusion model is not loaded" }
        val bytes = generateImageNative(
            handle, prompt, negativePrompt, width, height, steps, cfgScale, seed,
            NativeProgressListener(onProgress),
        ) ?: error("Stable Diffusion returned no image")
        rgbToBitmap(bytes, width, height)
    }

    @Synchronized
    fun close() {
        if (handle != 0L) {
            freeContextNative(handle)
            handle = 0L
        }
    }

    private fun rgbToBitmap(rgb: ByteArray, width: Int, height: Int): Bitmap {
        require(rgb.size == width * height * 3) { "Unexpected image buffer size" }
        val pixels = IntArray(width * height)
        pixels.indices.forEach { pixel ->
            val offset = pixel * 3
            pixels[pixel] = (0xff shl 24) or
                ((rgb[offset].toInt() and 0xff) shl 16) or
                ((rgb[offset + 1].toInt() and 0xff) shl 8) or
                (rgb[offset + 2].toInt() and 0xff)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private external fun loadModelNative(modelPath: String, threads: Int): Long
    private external fun generateImageNative(
        handle: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        progress: NativeProgressListener,
    ): ByteArray?
    private external fun freeContextNative(handle: Long)
}
