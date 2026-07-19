package com.hartmann.pixeldream.generation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hartmann.pixeldream.billing.BillingRepository
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.model.ModelRepository
import com.hartmann.pixeldream.model.ModelSessionManager
import com.hartmann.pixeldream.model.ModelStorage
import com.hartmann.pixeldream.model.SafetyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MANIFEST_URL =
    "https://storage.googleapis.com/pixeldream-app.firebasestorage.app/models/models_manifest.json"

/** Show an interstitial after every this-many generations for free-tier users. */
private const val GENERATIONS_PER_AD = 3

enum class GenerationStage { IDLE, CHECKING_PROMPT, ENHANCING, AWAITING_DIFFUSION, BLOCKED, ERROR }

data class GenerationUiState(
    val rawPrompt: String = "",
    val enhancedPrompt: String? = null,
    val stage: GenerationStage = GenerationStage.IDLE,
    val message: String? = null,
    val shouldShowAd: Boolean = false,
)

class GenerationViewModel(application: Application) : AndroidViewModel(application) {
    private val modelRepository = ModelRepository(application, MANIFEST_URL)
    private val sessionManager = ModelSessionManager(application, ModelStorage(application))
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
        _uiState.value = _uiState.value.copy(rawPrompt = text)
    }

    fun generate() {
        val prompt = _uiState.value.rawPrompt.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stage = GenerationStage.CHECKING_PROMPT, message = null)

            when (val rawCheck = sessionManager.checkSafety(prompt)) {
                is SafetyResult.Blocked -> {
                    _uiState.value = _uiState.value.copy(
                        stage = GenerationStage.BLOCKED,
                        message = "This idea can't be generated (${rawCheck.category}).",
                    )
                    return@launch
                }
                SafetyResult.Allowed -> Unit
            }

            val gemmaDescriptor = findDescriptor(ModelKind.GEMMA_PROMPT_ENHANCER)
            if (gemmaDescriptor == null) {
                _uiState.value = _uiState.value.copy(
                    stage = GenerationStage.ERROR,
                    message = "The prompt model isn't ready yet. Finish onboarding first.",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(stage = GenerationStage.ENHANCING)
            val enhanceResult = sessionManager.enhancePrompt(gemmaDescriptor, prompt)
            val enhanced = enhanceResult.getOrElse {
                _uiState.value = _uiState.value.copy(
                    stage = GenerationStage.ERROR,
                    message = it.message ?: "Couldn't enhance that prompt.",
                )
                return@launch
            }

            when (val enhancedCheck = sessionManager.checkSafety(enhanced)) {
                is SafetyResult.Blocked -> {
                    _uiState.value = _uiState.value.copy(
                        stage = GenerationStage.BLOCKED,
                        message = "This idea can't be generated (${enhancedCheck.category}).",
                    )
                    return@launch
                }
                SafetyResult.Allowed -> Unit
            }

            // TODO(Phase 3 continued): the diffusion model has no official
            // pre-converted artifact to download yet (see docs/models/README.md).
            // Once hosted, this is where ImageGenerator inference runs on
            // `enhanced` and produces the result bitmap.
            val triggerAd = maybeCountTowardAd()
            _uiState.value = _uiState.value.copy(
                stage = GenerationStage.AWAITING_DIFFUSION,
                enhancedPrompt = enhanced,
                message = "Prompt enhanced. Image generation isn't wired up yet.",
                shouldShowAd = triggerAd,
            )
        }
    }

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
        runCatching { modelRepository.fetchManifest() }
            .getOrNull()
            ?.firstOrNull { it.kind == kind && modelRepository.isReady(it) }

    override fun onCleared() {
        sessionManager.releaseImmediate()
    }
}
