package com.hartmann.pixeldream.model

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ENHANCER_SYSTEM_PREFIX =
    "Rewrite the following rough idea as a single, detailed, vivid prompt for " +
        "an image generation model. Describe subject, setting, lighting, and " +
        "style in one or two sentences. Respond with only the rewritten " +
        "prompt, no preamble.\n\nIdea: "

/** Gemma 4 prompt enhancement using Google's current LiteRT-LM runtime. */
class GemmaPromptEnhancer(
    private val context: Context,
    private val modelFile: File,
) {
    private var engine: Engine? = null

    private suspend fun ensureLoaded(): Engine = withContext(Dispatchers.IO) {
        engine ?: Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                // Explicitly bound the OpenMP thread pool rather than leaving it null
                // (library default = all available cores). Uncapped thread counts have
                // been linked to native SIGSEGV crashes inside libomp.so's task-invoke
                // path on devices with unusual core configurations (e.g. big.LITTLE
                // asymmetry, thermal core parking mid-inference).
                backend = Backend.CPU(
                    threadCount = safeThreadCount(),
                    numOfThreads = safeThreadCount(),
                ),
                cacheDir = context.cacheDir.absolutePath,
            ),
        ).also {
            it.initialize()
            engine = it
        }
    }

    private fun safeThreadCount(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    suspend fun enhance(rawPrompt: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val activeEngine = ensureLoaded()
            activeEngine.createConversation().use { conversation ->
                conversation.sendMessage(ENHANCER_SYSTEM_PREFIX + rawPrompt)
                    .contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                    .trim()
                    .ifEmpty { rawPrompt }
            }
        }
    }

    fun release() {
        engine?.close()
        engine = null
    }
}
