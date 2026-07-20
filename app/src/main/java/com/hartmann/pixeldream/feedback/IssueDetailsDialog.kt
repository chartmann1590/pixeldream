package com.hartmann.pixeldream.feedback

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailsDialog(
    report: BugReport,
    onDismiss: () -> Unit,
    viewModel: FeedbackViewModel = viewModel(),
) {
    var loadingIssue by remember { mutableStateOf(true) }
    var loadingComments by remember { mutableStateOf(true) }
    var postingComment by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf(report) }
    var comments by remember { mutableStateOf<List<GithubComment>>(emptyList()) }
    var issueError by remember { mutableStateOf<String?>(null) }
    var commentsError by remember { mutableStateOf<String?>(null) }
    var replyDraft by remember { mutableStateOf("") }
    var replyAttachment by remember { mutableStateOf<Uri?>(null) }
    var postError by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> replyAttachment = uri }

    LaunchedEffect(report.number) {
        loadingIssue = true
        issueError = null
        viewModel.refreshIssue(report) { result ->
            loadingIssue = false
            when (result) {
                is FeedbackResult.Success -> issue = result.value
                is FeedbackResult.Failure -> {
                    issueError = result.userMessage
                    issue = report
                }
            }
        }
        loadingComments = true
        commentsError = null
        viewModel.fetchComments(report) { result ->
            loadingComments = false
            when (result) {
                is FeedbackResult.Success -> comments = result.value
                is FeedbackResult.Failure -> commentsError = result.userMessage
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Issue #${issue.number}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StateChip(issue.status)
            }

            Text(issue.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Opened ${formatIso(issue.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            issueError?.let {
                Text(
                    "Couldn't refresh from GitHub: $it (showing local data).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text("Comments", style = MaterialTheme.typography.titleMedium)
            when {
                loadingComments -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Loading comments…", style = MaterialTheme.typography.bodySmall)
                }
                commentsError != null -> Text(
                    "Couldn't load comments: $commentsError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                comments.isEmpty() -> Text(
                    "No comments yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comments.forEach { CommentRow(it) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Add a reply", style = MaterialTheme.typography.titleMedium)

            replyAttachment?.let { uri ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Reply attachment preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { replyAttachment = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Remove attachment") }
                    }
                }
            }

            OutlinedTextField(
                value = replyDraft,
                onValueChange = { replyDraft = it },
                label = { Text("Reply") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
            )

            postError?.let {
                Text("Couldn't post: $postError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !postingComment,
                    modifier = Modifier.weight(1f),
                ) { Text(if (replyAttachment == null) "Attach" else "Replace") }
                Button(
                    onClick = {
                        postingComment = true
                        postError = null
                        viewModel.postComment(
                            report = issue,
                            text = replyDraft,
                            attachmentUri = replyAttachment,
                        ) { result ->
                            postingComment = false
                            when (result) {
                                is FeedbackResult.Success -> {
                                    replyDraft = ""
                                    replyAttachment = null
                                    comments = comments + result.value
                                }
                                is FeedbackResult.Failure -> postError = result.userMessage
                            }
                        }
                    },
                    enabled = !postingComment && (replyDraft.isNotBlank() || replyAttachment != null),
                    modifier = Modifier.weight(1f),
                ) {
                    if (postingComment) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Post reply")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: GithubComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${comment.user?.login ?: "unknown"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatIso(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StateChip(state: String) {
    val isOpen = state.equals("open", ignoreCase = true)
    AssistChip(
        onClick = {},
        label = { Text(state.replaceFirstChar { it.uppercase() }) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            labelColor = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}

private fun formatIso(iso: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(iso) ?: Date()
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsed)
}.getOrDefault(iso)
