package com.hartmann.pixeldream.generation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hartmann.pixeldream.ui.components.GenerativeProgressIndicator
import com.hartmann.pixeldream.ui.components.PixelDreamButton

@Composable
fun GenerationScreen() {
    val viewModel: GenerationViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("What should we create?", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = uiState.rawPrompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("a fox reading a book in a cozy library") },
        )

        PixelDreamButton(
            text = "Generate",
            onClick = viewModel::generate,
            enabled = uiState.rawPrompt.isNotBlank() &&
                uiState.stage != GenerationStage.CHECKING_PROMPT &&
                uiState.stage != GenerationStage.ENHANCING,
        )

        when (uiState.stage) {
            GenerationStage.CHECKING_PROMPT, GenerationStage.ENHANCING -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    GenerativeProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (uiState.stage == GenerationStage.ENHANCING) "Enhancing your prompt..." else "Checking your prompt...",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            GenerationStage.AWAITING_DIFFUSION -> {
                Text(uiState.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                uiState.enhancedPrompt?.let {
                    Text("\"$it\"", style = MaterialTheme.typography.bodyMedium)
                }
            }
            GenerationStage.BLOCKED, GenerationStage.ERROR -> {
                Text(uiState.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
            GenerationStage.IDLE -> Unit
        }
    }
}
