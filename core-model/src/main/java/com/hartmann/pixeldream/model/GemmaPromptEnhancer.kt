package com.hartmann.pixeldream.model

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ENHANCER_SYSTEM_PREFIX =
    "Rewrite the following rough idea as a single, detailed, vivid prompt for " +
        "an image generation model. Describe subject, setting, lighting, and " +
        "style in one or two sentences. Respond with only the rewritten " +
        "prompt, no preamble.\n\nIdea: "

/**
 * Wraps MediaPipe's [LlmInference] to turn a rough user idea into a detailed
 * image-generation prompt. The underlying engine is expensive to load, so
 * callers should reuse one instance per session via [ModelSessionManager]
 * rather than constructing this per request.
 */
class GemmaPromptEnhancer(
    private val context: Context,
    private val modelFile: File,
) {
    private var engine: LlmInference? = null

    private fun ensureLoaded(): LlmInference {
        return engine ?: run {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
                .build()
            LlmInference.createFromOptions(context, options).also { engine = it }
        }
    }

    suspend fun enhance(rawPrompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val llm = ensureLoaded()
            val response = llm.generateResponse(ENHANCER_SYSTEM_PREFIX + rawPrompt)
            response.trim().ifEmpty { rawPrompt }
        }
    }

    /** Releases the loaded engine. Call before loading the diffusion model on low-RAM devices. */
    fun release() {
        engine?.close()
        engine = null
    }
}
