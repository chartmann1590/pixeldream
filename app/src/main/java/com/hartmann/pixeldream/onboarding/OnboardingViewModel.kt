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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val deviceTier: DeviceTier? = null,
    val totalRamMb: Int = 0,
    val models: List<ModelDescriptor> = emptyList(),
    val downloadStates: Map<ModelKind, DownloadState> = emptyMap(),
    val manifestError: String? = null,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ModelRepository(application)
    private var downloadJob: Job? = null

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        Analytics.onboardingStarted()
        val tier = DeviceTierDetector.tierFor(application)
        val ramMb = DeviceTierDetector.totalRamMb(application)
        _uiState.value = _uiState.value.copy(deviceTier = tier, totalRamMb = ramMb)
    }

    fun loadManifestAndStartDownloads() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                val models = repository.fetchManifest()
                _uiState.value = _uiState.value.copy(
                    models = models,
                    manifestError = null,
                    downloadStates = models.associate { it.kind to DownloadState.Queued },
                )
                models.forEach { descriptor ->
                    if (repository.isReady(descriptor)) {
                        _uiState.value = _uiState.value.copy(
                            downloadStates = _uiState.value.downloadStates +
                                (descriptor.kind to DownloadState.Ready),
                        )
                        return@forEach
                    }
                    Analytics.modelDownloadStarted(descriptor.kind.name)
                    repository.download(descriptor).collect { state ->
                        _uiState.value = _uiState.value.copy(
                            downloadStates = _uiState.value.downloadStates + (descriptor.kind to state),
                        )
                        when (state) {
                            is DownloadState.Ready -> Analytics.modelDownloadCompleted(descriptor.kind.name)
                            is DownloadState.Failed -> Analytics.modelDownloadFailed(descriptor.kind.name, state.reason)
                            else -> Unit
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    manifestError = t.message ?: "Check your connection and try again.",
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
