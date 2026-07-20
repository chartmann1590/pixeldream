package com.hartmann.pixeldream.feedback

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedbackUiState(
    val reports: List<BugReport> = emptyList(),
    val isConfigured: Boolean = true,
    val configError: String? = null,
)

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FeedbackRepository(application)

    val uiState: StateFlow<FeedbackUiState> = repository.localReports
        .map { FeedbackUiState(reports = it, isConfigured = repository.isConfigured(), configError = repository.configError()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedbackUiState())

    val isConfigured: Boolean get() = repository.isConfigured()
    fun configError(): String? = repository.configError()

    fun submitReport(
        title: String,
        description: String,
        contactName: String,
        contactEmail: String,
        includeDiagnostics: Boolean,
        attachmentUri: Uri?,
        onResult: (FeedbackResult<BugReport>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(
                repository.submitReport(
                    title = title,
                    description = description,
                    contactName = contactName,
                    contactEmail = contactEmail,
                    includeDiagnostics = includeDiagnostics,
                    attachmentUri = attachmentUri,
                ),
            )
        }
    }

    fun refreshIssue(report: BugReport, onResult: (FeedbackResult<BugReport>) -> Unit) {
        viewModelScope.launch { onResult(repository.refreshIssue(report)) }
    }

    fun fetchComments(report: BugReport, onResult: (FeedbackResult<List<GithubComment>>) -> Unit) {
        viewModelScope.launch { onResult(repository.fetchComments(report)) }
    }

    fun postComment(
        report: BugReport,
        text: String,
        attachmentUri: Uri?,
        onResult: (FeedbackResult<GithubComment>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(repository.postComment(report, text, attachmentUri))
        }
    }
}
