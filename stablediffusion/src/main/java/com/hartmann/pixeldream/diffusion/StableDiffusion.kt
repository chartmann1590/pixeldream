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

    /** True when the loaded model runs on the GPU (Vulkan); false on the CPU or when nothing is loaded. */
    var isGpuActive: Boolean = false
        private set

    /** True when this build carries the GPU (Vulkan) backend at all. */
    val supportsGpu: Boolean get() = isGpuBuildNative()

    /**
     * The phone's GPU as reported by Vulkan, or null if it has none. Never loads a model.
     */
    fun gpuDevice(): VulkanDevice? = runCatching { gpuDeviceNative() }.getOrNull()?.let(VulkanDevice::parse)

    /**
     * Loads the model. With [useGpu] the GPU (Vulkan) backend is tried first; if the
     * phone has no usable Vulkan device the engine quietly uses the CPU instead, which
     * [isGpuActive] reports. With [useGpu] false the GPU is never touched.
     */
    suspend fun load(modelPath: String, threads: Int, useGpu: Boolean = false): Boolean =
        withContext(Dispatchers.Default) {
            close()
            handle = loadModelNative(modelPath, threads, useGpu)
            isGpuActive = handle != 0L && isGpuActiveNative()
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
        isGpuActive = false
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

    private external fun loadModelNative(modelPath: String, threads: Int, useGpu: Boolean): Long
    private external fun isGpuActiveNative(): Boolean
    private external fun isGpuBuildNative(): Boolean
    private external fun gpuDeviceNative(): String?
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

/** A Vulkan GPU: PCI-style vendor and device ids plus the driver's name for it. */
data class VulkanDevice(val vendorId: Int, val deviceId: Int, val name: String) {
    /** ARM Mali / Immortalis. */
    val isMali: Boolean get() = vendorId == VENDOR_ARM

    companion object {
        const val VENDOR_ARM = 0x13B5
        const val VENDOR_QUALCOMM = 0x5143
        const val VENDOR_IMAGINATION = 0x1010
        const val VENDOR_SAMSUNG = 0x144D

        /** Parses "vendorHex:deviceHex:name". */
        fun parse(text: String): VulkanDevice? {
            val parts = text.split(':', limit = 3)
            if (parts.size < 3) return null
            val vendor = parts[0].toIntOrNull(16) ?: return null
            val device = parts[1].toLongOrNull(16)?.toInt() ?: return null
            return VulkanDevice(vendor, device, parts[2])
        }
    }
}
