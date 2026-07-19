package com.hartmann.pixeldream.model

import android.content.Context
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

    suspend fun release() = lock.withLock {
        enhancer?.release()
        enhancer = null
    }

    /** Non-suspending teardown for use from lifecycle callbacks like `onCleared()`. */
    fun releaseImmediate() {
        enhancer?.release()
        enhancer = null
    }
}
