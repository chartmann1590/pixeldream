package com.hartmann.pixeldream.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hartmann.pixeldream.data.generation.GenerationEntity

@Composable
fun GalleryScreen(onOpenGeneration: (GenerationEntity) -> Unit) {
    val viewModel: GalleryViewModel = viewModel()
    val generations by viewModel.generations.collectAsState()

    if (generations.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nothing here yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Images you generate will show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
    ) {
        items(generations, key = { it.id }) { generation ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clickable { onOpenGeneration(generation) },
            ) {
                AsyncImage(
                    model = generation.thumbnailPath ?: generation.imageFilePath,
                    contentDescription = generation.originalPrompt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
