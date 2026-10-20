package com.hartmann.pixeldream.model

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.hartmann.pixeldream.diffusion.StableDiffusion
import java.io.File
import kotlinx.coroutines.CancellationException
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
        const val TIMING_TAG = "PixelDreamTiming"
    }

    private val lock = Mutex()
    private var enhancer: GemmaPromptEnhancer? = null
    private var imageGenerator: StableDiffusion? = null
    private val safetyChecker: PromptSafetyChecker = LocalPromptSafetyChecker()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gpu = GpuBackendStore(context).also { it.recoverFromCrash() }

    /** Whether the model was loaded with the GPU requested; null when nothing is loaded. */
    private var loadedWithGpuRequest: Boolean? = null

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
            val threads = GpuBackendStore.debugThreads() ?: Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
            val wantGpu = generator.supportsGpu && gpu.shouldTryGpu(DeviceTierDetector.tierFor(context))
            Log.i(TAG, "Image engine: threads=$threads wantGpu=$wantGpu")
            ensureLoaded(generator, modelFile.absolutePath, threads, wantGpu)

            suspend fun render(stepCount: Int, renderSeed: Long): Bitmap = generator.generate(
                prompt = prompt,
                negativePrompt = NEGATIVE_PROMPT,
                width = width,
                height = height,
                steps = stepCount,
                cfgScale = cfgScale,
                seed = renderSeed,
                onProgress = onProgress,
            )

            var started = SystemClock.elapsedRealtime()
            var bitmap: Bitmap
            if (generator.isGpuActive) {
                // GPU run, guarded: an error, a noise frame or a forced test failure switches
                // this phone to the CPU (remembered) and redoes the same image there.
                val gpuResult = runCatching {
                    gpu.beginGpuAttempt()
                    render(steps, seed)
                }
                gpu.endGpuAttempt()
                (gpuResult.exceptionOrNull() as? CancellationException)?.let { throw it }
                val gpuBitmap = gpuResult.getOrNull()
                val failure = when {
                    gpuBitmap == null -> "The GPU stopped with an error (${gpuResult.exceptionOrNull()?.message ?: "unknown"}), so PixelDream switched back to the CPU."
                    GpuBackendStore.debugFailure() == "noise" -> "Test switch: GPU image treated as noise."
                    looksDegenerate(gpuBitmap) -> "The GPU produced a noise image on this phone, so PixelDream switched back to the CPU."
                    else -> null
                }
                if (failure == null) {
                    bitmap = gpuBitmap!!
                } else {
                    gpuBitmap?.recycle()
                    gpu.recordFailure(failure)
                    reloadOnCpu(generator, modelFile.absolutePath, threads)
                    started = SystemClock.elapsedRealtime()
                    bitmap = render(steps, seed)
                }
            } else {
                bitmap = render(steps, seed)
            }
            if (looksDegenerate(bitmap)) {
                // A frame of pure noise or a blank frame is a run that never converged or a
                // decode that failed, not a picture. Try once more with the safe profile
                // (a fresh seed, at least SAFE_RETRY_STEPS steps) before giving up.
                Log.w(TAG, "Generated image looks degenerate; retrying once with the safe profile")
                bitmap.recycle()
                started = SystemClock.elapsedRealtime()
                bitmap = render(maxOf(steps, SAFE_RETRY_STEPS), seed + 1)
                check(!looksDegenerate(bitmap)) {
                    "That one came out as noise instead of a picture. Please try again, or try a simpler description."
                }
            }
            val seconds = (SystemClock.elapsedRealtime() - started) / 1000.0
            val onGpu = generator.isGpuActive
            gpu.recordRun(onGpu, seconds, width, steps)
            Log.i(TIMING_TAG, "backend=%s size=%dx%d steps=%d seconds=%.1f".format(if (onGpu) "GPU" else "CPU", width, height, steps, seconds))
            persist(bitmap)
        }
    }

    /** Loads the model if needed, trying the GPU first when [wantGpu], falling back to the CPU. */
    private suspend fun ensureLoaded(generator: StableDiffusion, path: String, threads: Int, wantGpu: Boolean) {
        if (generator.isLoaded && (loadedWithGpuRequest == wantGpu || (!wantGpu && !generator.isGpuActive))) return
        if (wantGpu) {
            val forced = GpuBackendStore.debugFailure() == "init"
            gpu.beginGpuAttempt()
            val loaded = if (forced) {
                Log.w(TAG, "Test switch: simulating a GPU load failure")
                generator.close()
                false
            } else {
                runCatching { generator.load(path, threads, useGpu = true) }
                    .onFailure { if (it is CancellationException) { gpu.endGpuAttempt(); throw it } }
                    .getOrDefault(false)
            }
            gpu.endGpuAttempt()
            when {
                loaded && generator.isGpuActive -> {
                    Log.i(TAG, "Model loaded on the GPU")
                    loadedWithGpuRequest = true
                    return
                }
                loaded -> {
                    // No usable Vulkan device: the engine already loaded on the CPU.
                    gpu.recordFailure("This phone has no GPU PixelDream can use, so it runs on the CPU.")
                    loadedWithGpuRequest = true
                    return
                }
                else -> gpu.recordFailure(
                    if (forced) "Test switch: GPU load failure." else "The model would not load on the GPU, so PixelDream switched back to the CPU.",
                )
            }
        }
        reloadOnCpu(generator, path, threads)
    }

    private suspend fun reloadOnCpu(generator: StableDiffusion, path: String, threads: Int) {
        loadedWithGpuRequest = null
        check(generator.load(path, threads, useGpu = false)) { "Could not load the Stable Diffusion model" }
        loadedWithGpuRequest = false
        Log.i(TAG, "Model loaded on the CPU")
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
        loadedWithGpuRequest = null
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
