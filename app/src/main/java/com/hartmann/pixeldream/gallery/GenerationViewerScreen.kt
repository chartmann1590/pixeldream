package com.hartmann.pixeldream.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hartmann.pixeldream.analytics.Analytics
import com.hartmann.pixeldream.feedback.ReportProblemDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GenerationViewerScreen(
    initialGenerationId: String,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val viewModel: GalleryViewModel = viewModel()
    val generations by viewModel.generations.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    if (generations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading imageâ€¦")
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        return
    }

    val startIndex = generations.indexOfFirst { it.id == initialGenerationId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { generations.size }
    val current = generations.getOrNull(pagerState.currentPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.background),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onClose) { Text("Close") }
            Text("IMAGE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${pagerState.currentPage + 1} / ${generations.size}", style = MaterialTheme.typography.labelLarge)
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val generation = generations[page]
            AsyncImage(
                model = generation.imageFilePath,
                contentDescription = generation.originalPrompt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            )
        }

        if (current != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Prompt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(current.originalPrompt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                statusMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Analytics.imageExported()
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    exportToGallery(context, File(current.imageFilePath))
                                }
                                statusMessage = if (saved) "Saved to Pictures/PixelDream" else "Could not save image"
                            }
                        },
                    ) { Text("Save") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            Analytics.imageShared()
                            context.startActivity(shareImageIntent(context, File(current.imageFilePath)))
                        },
                    ) { Text("Share") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onEdit(current.originalPrompt) },
                    ) { Text("Edit prompt") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showDeleteConfirmation = true },
                    ) { Text("Delete") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showReportDialog = true },
                ) { Text("Report offensive content") }
            }
        }
    }

    if (showReportDialog && current != null) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            confirmButton = {},
            text = {
                ReportProblemDialog(
                    onDismiss = { showReportDialog = false },
                    initialTitle = "Report generated image",
                    initialDescription = "Please explain why this generated image is offensive or unsafe.\n\nPrompt: ${current.originalPrompt}",
                    initialAttachmentUri = runCatching {
                        File(current.imageFilePath)
                            .takeIf(File::exists)
                            ?.let { imageContentUri(context, it) }
                    }.getOrNull(),
                    onSubmitted = {
                        viewModel.report(current.id, "Reported as offensive content")
                        Analytics.contentReported("offensive_content")
                    },
                )
            },
        )
    }

    if (showDeleteConfirmation && current != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this image?") },
            text = { Text("This removes the private image and its gallery record from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.delete(current)
                    onClose()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}
