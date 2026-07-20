package com.hartmann.pixeldream.model

import android.content.Context
import android.graphics.Bitmap
import com.hartmann.pixeldream.diffusion.StableDiffusion
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the lifecycle of the loaded Gemma session. MediaPipe task objects are
 * not safe for concurrent calls and are expensive to initialize, so all
 * inference is serialized through [lock]. On lower-RAM devices, [release]
 * should be called once the enhanced prompt is in hand and before the
 * diffusion model loads, rather than keeping both resident (see Phase 3
 * memory-management plan).
 */
class ModelSessionManager(
    private val context: Context,
    private val storage: ModelStorage,
) {
    private val lock = Mutex()
    private var enhancer: GemmaPromptEnhancer? = null
    private var imageGenerator: StableDiffusion? = null
    private val safetyChecker: PromptSafetyChecker = LocalPromptSafetyChecker()

    suspend fun enhancePrompt(descriptor: ModelDescriptor, rawPrompt: String): Result<String> =
        lock.withLock {
            val modelFile = storage.fileFor(descriptor)
            if (!modelFile.exists()) {
                return@withLock Result.failure(IllegalStateException("Gemma model not downloaded yet"))
            }
            val active = enhancer ?: GemmaPromptEnhancer(context, modelFile).also { enhancer = it }
            active.enhance(rawPrompt)
        }

    fun checkSafety(prompt: String): SafetyResult = safetyChecker.check(prompt)

    suspend fun generateImage(
        descriptor: ModelDescriptor,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        onProgress: (Int, Int) -> Unit,
    ): Result<File> = lock.withLock {
        runCatching {
            val modelFile = storage.fileFor(descriptor)
            check(modelFile.exists()) { "Stable Diffusion model not downloaded yet" }

            // The Pixel cannot keep Gemma and diffusion resident together reliably.
            enhancer?.release()
            enhancer = null

            val generator = imageGenerator ?: StableDiffusion().also { imageGenerator = it }
            if (!generator.isLoaded) {
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
                check(generator.load(modelFile.absolutePath, threads)) {
                    "Could not load the Stable Diffusion model"
                }
            }
            val bitmap = generator.generate(
                prompt = prompt,
                negativePrompt = "blurry, low quality, distorted, deformed, watermark, text",
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                seed = seed,
                onProgress = onProgress,
            )
            persist(bitmap)
        }
    }

    private fun persist(bitmap: Bitmap): File {
        val directory = File(context.filesDir, "generations").apply { mkdirs() }
        val output = File(directory, "pixeldream-${System.currentTimeMillis()}.png")
        output.outputStream().buffered().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Could not save image" }
        }
        return output
    }

    suspend fun release() = lock.withLock {
        enhancer?.release()
        enhancer = null
        imageGenerator?.close()
        imageGenerator = null
    }

    /** Non-suspending teardown for use from lifecycle callbacks like `onCleared()`. */
    fun releaseImmediate() {
        enhancer?.release()
        enhancer = null
    }
}
