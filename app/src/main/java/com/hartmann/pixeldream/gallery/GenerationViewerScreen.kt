package com.hartmann.pixeldream.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun GenerationViewerScreen(initialGenerationId: String, onClose: () -> Unit) {
    val viewModel: GalleryViewModel = viewModel()
    val generations by viewModel.generations.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (generations.isEmpty()) {
        onClose()
        return
    }

    val startIndex = generations.indexOfFirst { it.id == initialGenerationId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { generations.size }

    LaunchedEffect(generations.size) {
        if (generations.isEmpty()) onClose()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val generation = generations[page]
            AsyncImage(
                model = generation.imageFilePath,
                contentDescription = generation.originalPrompt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val current = generations.getOrNull(pagerState.currentPage)
        if (current != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(current.originalPrompt, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            exportToGallery(context, File(current.imageFilePath))
                        }
                    }) { Text("Save") }
                    TextButton(onClick = {
                        context.startActivity(shareImageIntent(context, File(current.imageFilePath)))
                    }) { Text("Share") }
                    TextButton(onClick = {
                        viewModel.report(current.id, "user_reported")
                    }) { Text("Report") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}
