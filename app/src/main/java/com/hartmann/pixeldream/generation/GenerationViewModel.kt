package com.hartmann.pixeldream.generation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hartmann.pixeldream.analytics.Analytics
import com.hartmann.pixeldream.billing.BillingRepository
import com.hartmann.pixeldream.data.generation.GenerationEntity
import com.hartmann.pixeldream.data.generation.RoomGenerationRepository
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.model.ModelRepository
import com.hartmann.pixeldream.model.ModelSessionManager
import com.hartmann.pixeldream.model.ModelStorage
import com.hartmann.pixeldream.model.SafetyResult
import com.hartmann.pixeldream.settings.GenerationPreferences
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.perf.performance
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val GENERATIONS_PER_AD = 3

enum class GenerationStage {
    IDLE, CHECKING_PROMPT, ENHANCING, LOADING_MODEL, GENERATING, SUCCESS, BLOCKED, ERROR,
}

data class GenerationUiState(
    val rawPrompt: String = "",
    val enhancedPrompt: String? = null,
    val stage: GenerationStage = GenerationStage.IDLE,
    val message: String? = null,
    val progressStep: Int = 0,
    val progressTotal: Int = 0,
    val imagePath: String? = null,
    val shouldShowAd: Boolean = false,
)

class GenerationViewModel(application: Application) : AndroidViewModel(application) {
    private val modelRepository = ModelRepository(application)
    private val sessionManager = ModelSessionManager(application, ModelStorage(application))
    private val generationRepository = RoomGenerationRepository(application)
    private val entitlementRepository = BillingRepository(application)
    private var generationsSinceLastAd = 0

    val isAdFree: StateFlow<Boolean> = entitlementRepository.isAdFree
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { entitlementRepository.refreshEntitlement() }
    }

    fun adShown() {
        _uiState.value = _uiState.value.copy(shouldShowAd = false)
    }

    fun updatePrompt(text: String) {
        _uiState.value = _uiState.value.copy(rawPrompt = text, message = null)
    }

    fun generate() {
        val prompt = _uiState.value.rawPrompt.trim()
        if (prompt.isEmpty() || isBusy(_uiState.value.stage)) return
        Analytics.promptSubmitted()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                stage = GenerationStage.CHECKING_PROMPT,
                message = null,
                imagePath = null,
                progressStep = 0,
                progressTotal = 0,
            )
            if (!allow(prompt)) return@launch

            val defaults = GenerationPreferences.read(getApplication())
            val gemma = findDescriptor(ModelKind.GEMMA_PROMPT_ENHANCER)
            val diffusion = findDescriptor(ModelKind.DIFFUSION_IMAGE_GENERATOR)
            if (diffusion == null || (defaults.enhancePrompts && gemma == null)) {
                fail("model_not_ready", "A required model is missing. Open Settings to download it.")
                return@launch
            }

            val enhanced = if (defaults.enhancePrompts) {
                _uiState.value = _uiState.value.copy(stage = GenerationStage.ENHANCING)
                val started = android.os.SystemClock.elapsedRealtime()
                val trace = Firebase.performance.newTrace("gemma_prompt_enhancement").apply { start() }
                val result = try {
                    sessionManager.enhancePrompt(gemma!!, prompt)
                } finally {
                    trace.stop()
                }
                result.getOrElse {
                    fail("enhance_failed", it.message ?: "Couldn't enhance that prompt.", it)
                    return@launch
                }.also { Analytics.promptEnhanced(android.os.SystemClock.elapsedRealtime() - started) }
            } else {
                prompt
            }
            if (!allow(enhanced)) return@launch

            _uiState.value = _uiState.value.copy(
                stage = GenerationStage.LOADING_MODEL,
                enhancedPrompt = enhanced,
                message = "Loading Stable Diffusion into memory…",
            )
            Analytics.generationStarted()
            val started = android.os.SystemClock.elapsedRealtime()
            val seed = System.currentTimeMillis()
            val trace = Firebase.performance.newTrace("offline_image_generation").apply {
                putAttribute("model_version", diffusion.version.take(100))
                putAttribute("image_size", defaults.imageSize.toString())
                putMetric("diffusion_steps", defaults.steps.toLong())
                start()
            }
            val result = try {
                sessionManager.generateImage(
                    descriptor = diffusion,
                    prompt = enhanced,
                    width = defaults.imageSize,
                    height = defaults.imageSize,
                    steps = defaults.steps,
                    cfgScale = defaults.cfgScale,
                    seed = seed,
                ) { step, total ->
                    _uiState.value = _uiState.value.copy(
                        stage = GenerationStage.GENERATING,
                        progressStep = step,
                        progressTotal = total,
                        message = "Generating entirely on this phone…",
                    )
                }
            } finally {
                trace.stop()
            }
            val file = result.getOrElse {
                fail("diffusion_failed", it.message ?: "Image generation failed.", it)
                return@launch
            }
            generationRepository.save(
                GenerationEntity(
                    id = UUID.randomUUID().toString(),
                    originalPrompt = prompt,
                    enhancedPrompt = enhanced,
                    imageFilePath = file.absolutePath,
                    thumbnailPath = null,
                    createdAt = System.currentTimeMillis(),
                    modelVersion = diffusion.version,
                ),
            )
            Analytics.generationSucceeded(android.os.SystemClock.elapsedRealtime() - started, diffusion.version)
            _uiState.value = _uiState.value.copy(
                stage = GenerationStage.SUCCESS,
                imagePath = file.absolutePath,
                message = "Created offline and saved to your private gallery.",
                shouldShowAd = maybeCountTowardAd(),
            )
        }
    }

    private fun allow(prompt: String): Boolean = when (val check = sessionManager.checkSafety(prompt)) {
        is SafetyResult.Blocked -> {
            Analytics.promptBlockedByFilter(check.category)
            _uiState.value = _uiState.value.copy(
                stage = GenerationStage.BLOCKED,
                message = "This idea can't be generated (${check.category}).",
            )
            false
        }
        SafetyResult.Allowed -> true
    }

    private fun fail(type: String, message: String, throwable: Throwable? = null) {
        Analytics.generationFailed(type)
        Firebase.crashlytics.setCustomKey("generation_error_type", type)
        Firebase.crashlytics.log(message)
        throwable?.let(Firebase.crashlytics::recordException)
        _uiState.value = _uiState.value.copy(stage = GenerationStage.ERROR, message = message)
    }

    private fun isBusy(stage: GenerationStage) = stage in setOf(
        GenerationStage.CHECKING_PROMPT,
        GenerationStage.ENHANCING,
        GenerationStage.LOADING_MODEL,
        GenerationStage.GENERATING,
    )

    private fun maybeCountTowardAd(): Boolean {
        if (isAdFree.value) return false
        generationsSinceLastAd++
        if (generationsSinceLastAd >= GENERATIONS_PER_AD) {
            generationsSinceLastAd = 0
            return true
        }
        return false
    }

    private suspend fun findDescriptor(kind: ModelKind): ModelDescriptor? =
        modelRepository.fetchManifest().firstOrNull { it.kind == kind && modelRepository.isReady(it) }

    override fun onCleared() {
        sessionManager.releaseImmediate()
    }
}
