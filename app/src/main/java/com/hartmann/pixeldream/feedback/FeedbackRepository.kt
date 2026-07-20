package com.hartmann.pixeldream.feedback

import android.content.Context
import android.net.Uri
import com.hartmann.pixeldream.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of an attempt to do something against GitHub. Failures carry a
 * user-presentable message and never expose the token or request details.
 */
sealed interface FeedbackResult<out T> {
    data class Success<T>(val value: T) : FeedbackResult<T>
    data class Failure(val userMessage: String) : FeedbackResult<Nothing>
}

/**
 * Orchestrates the in-app feedback flow: optional screenshot upload, issue
 * creation, issue fetch, comments fetch, and comment posting. Combines
 * [GithubApi], [DiagnosticsHelper], [ImageEncoder], and [BugReportRepo].
 */
class FeedbackRepository(private val context: Context) {

    private val api = GithubClient.api
    private val repo = BugReportRepo(context)
    private val assetDir: String = BuildConfig.FEEDBACK_ASSETS_DIR.ifBlank { "feedback-assets" }

    val localReports = repo.bugReports

    fun isConfigured(): Boolean = GithubClient.isConfigured

    fun configError(): String? {
        val missing = mutableListOf<String>()
        if (BuildConfig.FEEDBACK_PROXY_URL.isBlank()) missing += "feedback service"
        if (BuildConfig.GITHUB_REPO_OWNER.isBlank()) missing += "repo owner"
        if (BuildConfig.GITHUB_REPO_NAME.isBlank()) missing += "repo name"
        return if (missing.isEmpty()) null
        else "Feedback is not configured (missing ${missing.joinToString(", ")})."
    }

    suspend fun submitReport(
        title: String,
        description: String,
        contactName: String,
        contactEmail: String,
        includeDiagnostics: Boolean,
        attachmentUri: Uri?,
    ): FeedbackResult<BugReport> = withContext(Dispatchers.IO) {
        configError()?.let { return@withContext FeedbackResult.Failure(it) }
        if (title.isBlank()) return@withContext FeedbackResult.Failure("Title is required.")
        if (description.isBlank()) return@withContext FeedbackResult.Failure("Description is required.")

        try {
            val attachmentMarkdown = uploadAttachment(attachmentUri)

            val diagnostics = if (includeDiagnostics) {
                DiagnosticsHelper.collect(context)
            } else {
                "_Diagnostics omitted by user._"
            }

            val body = buildString {
                appendLine("## Description")
                appendLine()
                appendLine(description.trim())
                appendLine()
                appendLine("## Contact Info")
                appendLine()
                appendLine("- Name: ${contactName.ifBlank { "Not provided" }}")
                appendLine("- Email: ${contactEmail.ifBlank { "Not provided" }}")
                appendLine()
                if (attachmentMarkdown != null) {
                    appendLine("## Attachment")
                    appendLine()
                    appendLine(attachmentMarkdown)
                    appendLine()
                }
                appendLine(diagnostics.trim())
            }

            val issue = api.createIssue(
                owner = BuildConfig.GITHUB_REPO_OWNER,
                repo = BuildConfig.GITHUB_REPO_NAME,
                body = CreateIssueRequest(title = "[Feedback] ${title.trim()}", body = body),
            )

            val summary = BugReport(
                number = issue.number,
                title = issue.title,
                status = issue.state,
                createdAt = issue.createdAt,
                htmlUrl = issue.htmlUrl,
            )
            repo.saveBugReport(summary)
            FeedbackResult.Success(summary)
        } catch (t: Throwable) {
            FeedbackResult.Failure(friendly(t))
        }
    }

    suspend fun refreshIssue(report: BugReport): FeedbackResult<BugReport> = withContext(Dispatchers.IO) {
        if (!GithubClient.isConfigured) return@withContext FeedbackResult.Failure(configError() ?: "Not configured.")
        try {
            val issue = api.getIssue(
                owner = BuildConfig.GITHUB_REPO_OWNER,
                repo = BuildConfig.GITHUB_REPO_NAME,
                number = report.number,
            )
            val updated = report.copy(
                title = issue.title,
                status = issue.state,
                createdAt = issue.createdAt,
                htmlUrl = issue.htmlUrl,
            )
            repo.saveBugReport(updated)
            FeedbackResult.Success(updated)
        } catch (t: Throwable) {
            FeedbackResult.Failure(friendly(t))
        }
    }

    suspend fun fetchComments(report: BugReport): FeedbackResult<List<GithubComment>> = withContext(Dispatchers.IO) {
        if (!GithubClient.isConfigured) return@withContext FeedbackResult.Failure(configError() ?: "Not configured.")
        try {
            val comments = api.listComments(
                owner = BuildConfig.GITHUB_REPO_OWNER,
                repo = BuildConfig.GITHUB_REPO_NAME,
                number = report.number,
            )
            FeedbackResult.Success(comments)
        } catch (t: Throwable) {
            FeedbackResult.Failure(friendly(t))
        }
    }

    suspend fun postComment(
        report: BugReport,
        text: String,
        attachmentUri: Uri?,
    ): FeedbackResult<GithubComment> = withContext(Dispatchers.IO) {
        if (!GithubClient.isConfigured) return@withContext FeedbackResult.Failure(configError() ?: "Not configured.")
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachmentUri == null) {
            return@withContext FeedbackResult.Failure("Reply cannot be empty.")
        }
        try {
            val attachmentMarkdown = uploadAttachment(attachmentUri)
            val body = buildString {
                appendLine("## Reply")
                appendLine()
                appendLine(trimmed.ifBlank { "_(attachment only)_" })
                if (attachmentMarkdown != null) {
                    appendLine()
                    appendLine("## Attachment")
                    appendLine()
                    appendLine(attachmentMarkdown)
                }
            }
            val comment = api.postComment(
                owner = BuildConfig.GITHUB_REPO_OWNER,
                repo = BuildConfig.GITHUB_REPO_NAME,
                number = report.number,
                body = PostCommentRequest(body = body),
            )
            FeedbackResult.Success(comment)
        } catch (t: Throwable) {
            FeedbackResult.Failure(friendly(t))
        }
    }

    private suspend fun uploadAttachment(uri: Uri?): String? {
        if (uri == null) return null
        val base64 = ImageEncoder.uriToBase64(context, uri)
        val fileName = ImageEncoder.uniqueAssetName()
        val response = api.uploadAsset(
            owner = BuildConfig.GITHUB_REPO_OWNER,
            repo = BuildConfig.GITHUB_REPO_NAME,
            path = "$assetDir/$fileName",
            body = UploadAssetRequest(message = "Add feedback attachment for issue", content = base64),
        )
        val url = response.content?.downloadUrl
            ?: return throw RuntimeException("GitHub did not return a download URL for the screenshot.")
        return "![Screenshot]($url)"
    }

    private fun friendly(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "Network request timed out. Try again."
        else -> t.message?.take(300)?.ifBlank { "Unexpected error." } ?: "Unexpected error."
    }
}
