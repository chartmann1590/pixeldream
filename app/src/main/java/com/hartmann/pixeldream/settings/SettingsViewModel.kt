package com.hartmann.pixeldream.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hartmann.pixeldream.model.DownloadState
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.model.ModelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val models: List<ModelDescriptor> = emptyList(),
    val states: Map<ModelKind, DownloadState?> = emptyMap(),
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ModelRepository(application)
    private val jobs = mutableMapOf<ModelKind, Job>()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val models = repository.fetchManifest()
            val states = models.associate { descriptor ->
                descriptor.kind to if (repository.isReady(descriptor)) DownloadState.Ready else null
            }
            _uiState.value = SettingsUiState(models, states)
        }
    }

    fun download(descriptor: ModelDescriptor, replace: Boolean = false) {
        if (jobs[descriptor.kind]?.isActive == true) return
        jobs[descriptor.kind] = viewModelScope.launch {
            if (replace) repository.delete(descriptor)
            repository.download(descriptor).collect { state ->
                _uiState.value = _uiState.value.copy(
                    states = _uiState.value.states + (descriptor.kind to state),
                )
            }
        }
    }

    fun delete(descriptor: ModelDescriptor) {
        jobs.remove(descriptor.kind)?.cancel()
        viewModelScope.launch {
            repository.delete(descriptor)
            _uiState.value = _uiState.value.copy(
                states = _uiState.value.states + (descriptor.kind to null),
            )
        }
    }
}
