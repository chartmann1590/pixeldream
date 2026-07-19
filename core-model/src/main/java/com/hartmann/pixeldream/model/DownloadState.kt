package com.hartmann.pixeldream.model

/** Progress state for a single model download, as tracked during onboarding. */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState
    data object Verifying : DownloadState
    data object Ready : DownloadState
    data class Failed(val reason: String) : DownloadState
}
