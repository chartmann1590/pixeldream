package com.hartmann.pixeldream.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hartmann.pixeldream.data.generation.GenerationEntity

@Composable
fun GalleryScreen(onOpenGeneration: (GenerationEntity) -> Unit) {
    val viewModel: GalleryViewModel = viewModel()
    val generations by viewModel.generations.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f), MaterialTheme.colorScheme.background))),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text("GALLERY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text("Your creations", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                if (generations.isEmpty()) "A private collection, stored only on this phone."
                else "${generations.size} image${if (generations.size == 1) "" else "s"} stored on this phone",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (generations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Your first image starts in Create", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(generations, key = { it.id }) { generation ->
                    Card(
                        onClick = { onOpenGeneration(generation) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
    }
}
