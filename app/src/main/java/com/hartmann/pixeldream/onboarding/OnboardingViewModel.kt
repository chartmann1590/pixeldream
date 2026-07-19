package com.hartmann.pixeldream.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hartmann.pixeldream.analytics.Analytics
import com.hartmann.pixeldream.model.DeviceTier
import com.hartmann.pixeldream.model.DeviceTierDetector
import com.hartmann.pixeldream.model.DownloadState
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.model.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Served by the pixeldream-model-proxy Cloudflare Worker, which proxies live
// to Google's official litert-community Hugging Face repos (auth injected
// server-side since Gemma downloads are gated -- see
// cloudflare-worker/README.md). No model bytes are stored on our own
// infrastructure; every byte comes from huggingface.co per request.
private const val MANIFEST_URL =
    "https://pixeldream-model-proxy.charles-h-hartmann1.workers.dev/models/manifest.json"

data class OnboardingUiState(
    val deviceTier: DeviceTier? = null,
    val totalRamMb: Int = 0,
    val models: List<ModelDescriptor> = emptyList(),
    val downloadStates: Map<ModelKind, DownloadState> = emptyMap(),
    val manifestError: String? = null,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ModelRepository(application, MANIFEST_URL)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        Analytics.onboardingStarted()
        val tier = DeviceTierDetector.tierFor(application)
        val ramMb = DeviceTierDetector.totalRamMb(application)
        _uiState.value = _uiState.value.copy(deviceTier = tier, totalRamMb = ramMb)
    }

    fun loadManifestAndStartDownloads() {
        viewModelScope.launch {
            try {
                val models = repository.fetchManifest()
                _uiState.value = _uiState.value.copy(models = models, manifestError = null)
                models.forEach { descriptor ->
                    Analytics.modelDownloadStarted(descriptor.kind.name)
                    viewModelScope.launch {
                        repository.download(descriptor).collect { state ->
                            _uiState.value = _uiState.value.copy(
                                downloadStates = _uiState.value.downloadStates + (descriptor.kind to state),
                            )
                            when (state) {
                                is com.hartmann.pixeldream.model.DownloadState.Ready ->
                                    Analytics.modelDownloadCompleted(descriptor.kind.name)
                                is com.hartmann.pixeldream.model.DownloadState.Failed ->
                                    Analytics.modelDownloadFailed(descriptor.kind.name, state.reason)
                                else -> Unit
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    manifestError = "Check your connection and try again.",
                )
            }
        }
    }

    fun allModelsReady(): Boolean {
        val state = _uiState.value
        return state.models.isNotEmpty() &&
            state.models.all { state.downloadStates[it.kind] == DownloadState.Ready }
    }
}
