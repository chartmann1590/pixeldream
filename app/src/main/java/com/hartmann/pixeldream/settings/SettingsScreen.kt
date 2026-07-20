package com.hartmann.pixeldream.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hartmann.pixeldream.feedback.SupportFeedbackCard
import com.hartmann.pixeldream.model.DownloadState
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.ads.ConsentManager

@Composable
fun SettingsScreen(onOpenAdFree: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }
    var defaults by remember { mutableStateOf(GenerationPreferences.read(context)) }
    val privacyOptionsRequired = remember { ConsentManager.isPrivacyOptionsRequired(context) }

    fun save(updated: GenerationDefaults) {
        defaults = updated
        GenerationPreferences.write(context, updated)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.tertiary.copy(alpha = .09f), MaterialTheme.colorScheme.background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text("Make PixelDream yours", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Models, generation quality, storage, and app information.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))

        SectionTitle("Offline models")
        state.models.forEach { descriptor ->
            ModelCard(
                descriptor = descriptor,
                state = state.states[descriptor.kind],
                onDownload = { viewModel.download(descriptor) },
                onRedownload = { viewModel.download(descriptor, replace = true) },
                onDelete = { viewModel.delete(descriptor) },
            )
            Spacer(Modifier.height(12.dp))
        }

        SectionTitle("Generation defaults")
        SettingsCard {
            SettingRow("Enhance with Gemma 4", "Richer prompts before diffusion") {
                Switch(defaults.enhancePrompts, { save(defaults.copy(enhancePrompts = it)) })
            }
            HorizontalDivider()
            Text("Quality steps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
            ChoiceRow(listOf(8, 12, 20), defaults.steps) { save(defaults.copy(steps = it)) }
            ChoiceRow(listOf(30, 40), defaults.steps) { save(defaults.copy(steps = it)) }
            Text("Image size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
            ChoiceRow(listOf(256, 512), defaults.imageSize, suffix = " px") { save(defaults.copy(imageSize = it)) }
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("PixelDream")
        SettingsCard {
            SettingRow("Ad-free", "Manage the optional upgrade") {
                OutlinedButton(onClick = onOpenAdFree) { Text("Open") }
            }
            HorizontalDivider()
            SettingRow("Privacy policy", "How ads, diagnostics, and optional feedback use data") {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://pixeldream-app.web.app/privacy/"),
                        ),
                    )
                }) { Text("View") }
            }
            if (privacyOptionsRequired) {
                HorizontalDivider()
                SettingRow("Privacy choices", "Manage personalized advertising consent") {
                    OutlinedButton(onClick = {
                        (context as? Activity)?.let(ConsentManager::showPrivacyOptions)
                    }) { Text("Open") }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version $versionName\nOffline image generation powered by Gemma 4, Stable Diffusion 1.5, LiteRT-LM, and stable-diffusion.cpp. Prompts and images stay on this device unless you explicitly attach them to a feedback report or share them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://pixeldream-app.web.app/licenses/"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Model and open-source licenses") }
        }
        Spacer(Modifier.height(18.dp))

        SupportFeedbackCard()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ModelCard(
    descriptor: ModelDescriptor,
    state: DownloadState?,
    onDownload: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = when (descriptor.kind) {
        ModelKind.GEMMA_PROMPT_ENHANCER -> "Gemma 4 prompt enhancer"
        ModelKind.DIFFUSION_IMAGE_GENERATOR -> "Stable Diffusion image generator"
    }
    val status = when (state) {
        DownloadState.Ready -> "Ready • ${formatSize(descriptor.sizeBytes)}"
        DownloadState.Queued -> "Queued"
        DownloadState.Verifying -> "Verifying download…"
        is DownloadState.Downloading -> "Downloading ${percent(state)}% • ${formatSize(state.bytesDownloaded)}"
        is DownloadState.Failed -> state.reason
        null -> "Not downloaded • ${formatSize(descriptor.sizeBytes)}"
    }
    SettingsCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(status, color = if (state is DownloadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                DownloadState.Ready -> {
                    OutlinedButton(onClick = onRedownload) { Text("Redownload") }
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                is DownloadState.Downloading, DownloadState.Queued, DownloadState.Verifying -> Unit
                else -> Button(onClick = onDownload) { Text(if (state is DownloadState.Failed) "Retry" else "Download") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
private fun ChoiceRow(values: List<Int>, selected: Int, suffix: String = " steps", onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            if (value == selected) Button(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) { Text("$value$suffix") }
            else OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) { Text("$value$suffix") }
        }
    }
}

private fun percent(state: DownloadState.Downloading): Int =
    if (state.totalBytes <= 0) 0 else ((state.bytesDownloaded * 100) / state.totalBytes).toInt().coerceIn(0, 100)

private fun formatSize(bytes: Long): String = if (bytes >= 1_073_741_824L) {
    "%.1f GB".format(bytes / 1_073_741_824.0)
} else "%.0f MB".format(bytes / 1_048_576.0)
