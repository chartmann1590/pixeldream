package com.hartmann.pixeldream.generation

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hartmann.pixeldream.ads.InterstitialAdManager
import com.hartmann.pixeldream.ui.components.GenerativeProgressIndicator
import com.hartmann.pixeldream.ui.components.PixelDreamButton
import coil.compose.AsyncImage

@Composable
fun GenerationScreen(
    adsReady: Boolean = false,
    initialPrompt: String? = null,
    onInitialPromptConsumed: () -> Unit = {},
) {
    val viewModel: GenerationViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val adManager = remember { InterstitialAdManager(context) }

    LaunchedEffect(adsReady) { if (adsReady) adManager.preload() }
    LaunchedEffect(initialPrompt) {
        if (initialPrompt != null) {
            viewModel.updatePrompt(initialPrompt)
            onInitialPromptConsumed()
        }
    }
    LaunchedEffect(uiState.shouldShowAd) {
        if (uiState.shouldShowAd) {
            val activity = context as? Activity
            if (adsReady && activity != null) {
                adManager.showIfLoaded(activity) { viewModel.adShown() }
            } else {
                viewModel.adShown()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), MaterialTheme.colorScheme.background),
                ),
            )
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("CREATE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("What are you imagining?", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Describe the moment. Gemma 4 will shape it into a richer visual prompt.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = uiState.rawPrompt,
                    onValueChange = { viewModel.updatePrompt(it.take(500)) },
                    modifier = Modifier.fillMaxWidth().height(156.dp),
                    placeholder = { Text("A fox reading beside a rainy library window…") },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Runs privately on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${uiState.rawPrompt.length}/500", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                PixelDreamButton(
                    text = when (uiState.stage) {
                        GenerationStage.CHECKING_PROMPT -> "Checking…"
                        GenerationStage.ENHANCING -> "Enhancing…"
                        GenerationStage.LOADING_MODEL -> "Loading model…"
                        GenerationStage.GENERATING -> "Generating ${uiState.progressStep}/${uiState.progressTotal}…"
                        else -> "Create image  ✦"
                    },
                    onClick = viewModel::generate,
                    enabled = uiState.rawPrompt.isNotBlank() &&
                        uiState.stage != GenerationStage.CHECKING_PROMPT &&
                        uiState.stage != GenerationStage.ENHANCING &&
                        uiState.stage != GenerationStage.LOADING_MODEL &&
                        uiState.stage != GenerationStage.GENERATING,
                )
            }
        }

        when (uiState.stage) {
            GenerationStage.CHECKING_PROMPT, GenerationStage.ENHANCING,
            GenerationStage.LOADING_MODEL, GenerationStage.GENERATING -> {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GenerativeProgressIndicator(Modifier.height(72.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (uiState.stage) {
                            GenerationStage.ENHANCING -> "Gemma 4 is refining your idea…"
                            GenerationStage.LOADING_MODEL -> "Loading Stable Diffusion…"
                            GenerationStage.GENERATING -> "Diffusion step ${uiState.progressStep} of ${uiState.progressTotal}"
                            else -> "Checking your prompt…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            GenerationStage.SUCCESS, GenerationStage.BLOCKED, GenerationStage.ERROR -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (uiState.stage == GenerationStage.BLOCKED || uiState.stage == GenerationStage.ERROR) {
                        MaterialTheme.colorScheme.errorContainer
                    } else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(uiState.message.orEmpty(), fontWeight = FontWeight.SemiBold)
                        uiState.imagePath?.let { imagePath ->
                            Spacer(Modifier.height(14.dp))
                            AsyncImage(
                                model = imagePath,
                                contentDescription = uiState.rawPrompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                            )
                        }
                        uiState.enhancedPrompt?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            GenerationStage.IDLE -> Unit
        }
    }
}
