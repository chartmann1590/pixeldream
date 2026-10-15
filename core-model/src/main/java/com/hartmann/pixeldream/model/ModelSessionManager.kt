package com.hartmann.pixeldream.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hartmann.pixeldream.diffusion.StableDiffusion
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private companion object {
        const val TAG = "ModelSessionManager"
        const val NEGATIVE_PROMPT = "blurry, low quality, distorted, deformed, watermark, text"
        const val SAFE_RETRY_STEPS = 20
    }

    private val lock = Mutex()
    private var enhancer: GemmaPromptEnhancer? = null
    private var imageGenerator: StableDiffusion? = null
    private val safetyChecker: PromptSafetyChecker = LocalPromptSafetyChecker()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            var bitmap = generator.generate(
                prompt = prompt,
                negativePrompt = NEGATIVE_PROMPT,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                seed = seed,
                onProgress = onProgress,
            )
            if (looksDegenerate(bitmap)) {
                // A frame of pure noise or a blank frame is a run that never converged or a
                // decode that failed, not a picture. Try once more with the safe profile
                // (a fresh seed, at least SAFE_RETRY_STEPS steps) before giving up.
                Log.w(TAG, "Generated image looks degenerate; retrying once with the safe profile")
                bitmap.recycle()
                bitmap = generator.generate(
                    prompt = prompt,
                    negativePrompt = NEGATIVE_PROMPT,
                    width = width,
                    height = height,
                    steps = maxOf(steps, SAFE_RETRY_STEPS),
                    cfgScale = cfgScale,
                    seed = seed + 1,
                    onProgress = onProgress,
                )
                check(!looksDegenerate(bitmap)) {
                    "That one came out as noise instead of a picture. Please try again, or try a simpler description."
                }
            }
            persist(bitmap)
        }
    }

    private fun looksDegenerate(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val stats = ImageSanity.stats(pixels, bitmap.width, bitmap.height)
        Log.i(TAG, "Image sanity: std=%.1f neighbour=%.1f ratio=%.2f verdict=%s".format(stats.std, stats.neighbourDiff, stats.ratio, stats.verdict))
        return stats.verdict != ImageSanity.Verdict.OK
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

    /**
     * Lifecycle-safe teardown for callbacks like `onCleared()`. Cleanup waits
     * for an in-flight native generation to release [lock], avoiding both a
     * native use-after-free and a multi-gigabyte model leak.
     */
    fun releaseImmediate() {
        cleanupScope.launch { release() }
    }
}
