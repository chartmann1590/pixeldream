package com.hartmann.pixeldream.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hartmann.pixeldream.model.DeviceTier
import com.hartmann.pixeldream.model.DownloadState
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.model.ModelKind
import com.hartmann.pixeldream.ui.components.PixelDreamButton

@Composable
private fun OnboardingPage(content: @Composable (Modifier) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.primary.copy(alpha = 0.14f), colors.background, colors.secondary.copy(alpha = 0.08f)),
                ),
            ),
    ) {
        Box(
            Modifier
                .padding(top = 36.dp, end = 18.dp)
                .size(150.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.08f)),
        )
        content(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp))
    }
}

@Composable
private fun Eyebrow(text: String) {
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), shape = CircleShape) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) = OnboardingPage { modifier ->
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Eyebrow("Private • On device")
            Spacer(Modifier.height(24.dp))
            Text("PixelDream", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Turn a simple thought into something worth looking at.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(28.dp))
            FeatureCard("✦", "Create locally", "Your prompts and images stay on this phone.")
            Spacer(Modifier.height(12.dp))
            FeatureCard("◎", "Works offline", "Download the models once, then create anywhere.")
        }
        PixelDreamButton(text = "Set up PixelDream", onClick = onContinue)
    }
}

@Composable
private fun FeatureCard(symbol: String, title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)) {
                Text(symbol, Modifier.padding(11.dp), color = MaterialTheme.colorScheme.secondary)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ContentPolicyScreen(onAgree: () -> Unit) = OnboardingPage { modifier ->
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Eyebrow("One quick note")
            Spacer(Modifier.height(22.dp))
            Text("Create responsibly", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "PixelDream checks prompts on your device to keep creation safe.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PolicyRow("Protect children", "Never create sexual content involving minors.")
                    PolicyRow("Respect people", "No harassment, impersonation, or targeted abuse.")
                    PolicyRow("Avoid graphic harm", "Don’t create extreme or exploitative violence.")
                }
            }
        }
        PixelDreamButton(text = "I agree and understand", onClick = onAgree)
    }
}

@Composable
private fun PolicyRow(title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DeviceCheckScreen(deviceTier: DeviceTier?, totalRamMb: Int, onContinue: () -> Unit) = OnboardingPage { modifier ->
    val ramGb = totalRamMb / 1024.0
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Eyebrow("Device check")
            Spacer(Modifier.height(22.dp))
            Text("Ready to create", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("${"%.1f".format(ramGb)} GB", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text("available system memory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        when (deviceTier) {
                            DeviceTier.RECOMMENDED -> "Great fit. Your phone has enough memory for smooth on-device processing."
                            DeviceTier.LOW -> "Compatible. Creation may take longer, so keep PixelDream open while it works."
                            null -> "Checking your phone…"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        PixelDreamButton(text = "Continue", onClick = onContinue, enabled = deviceTier != null)
    }
}

@Composable
fun ModelDownloadScreen(
    models: List<ModelDescriptor>,
    downloadStates: Map<ModelKind, DownloadState>,
    manifestError: String?,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    allReady: Boolean,
) = OnboardingPage { modifier ->
    val failed = downloadStates.values.any { it is DownloadState.Failed }
    Column(modifier = modifier) {
        Eyebrow("Final setup")
        Spacer(Modifier.height(18.dp))
        Text(if (allReady) "You’re ready" else "Downloading AI models", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (allReady) "Both models are verified and ready offline."
            else "About 4.1 GB total. You can leave and come back—the system will keep downloading.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (manifestError != null) {
            ErrorCard(manifestError)
        } else if (models.isEmpty()) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Text("Preparing official model downloads…", Modifier.padding(20.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(models, key = { it.kind }) { descriptor ->
                    ModelDownloadCard(descriptor, downloadStates[descriptor.kind])
                }
            }
        }

        when {
            allReady -> PixelDreamButton(text = "Start creating", onClick = onContinue)
            failed || manifestError != null -> PixelDreamButton(text = "Retry downloads", onClick = onStart)
            models.isEmpty() -> PixelDreamButton(text = "Start download", onClick = onStart)
        }
    }
}

@Composable
private fun ModelDownloadCard(descriptor: ModelDescriptor, state: DownloadState?) {
    val title = when (descriptor.kind) {
        ModelKind.GEMMA_PROMPT_ENHANCER -> "Gemma 4"
        ModelKind.DIFFUSION_IMAGE_GENERATOR -> "Stable Diffusion 1.5"
    }
    val subtitle = when (descriptor.kind) {
        ModelKind.GEMMA_PROMPT_ENHANCER -> "Prompt enhancer • ${formatSize(descriptor.sizeBytes)}"
        ModelKind.DIFFUSION_IMAGE_GENERATOR -> "Image foundation • ${formatSize(descriptor.sizeBytes)}"
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stateLabel(state), color = stateColor(state), style = MaterialTheme.typography.labelLarge)
            }
            if (state is DownloadState.Downloading) {
                Spacer(Modifier.height(14.dp))
                val progress = (state.bytesDownloaded.toFloat() / state.totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "${formatSize(state.bytesDownloaded)} of ${formatSize(state.totalBytes)} • ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is DownloadState.Failed) {
                Spacer(Modifier.height(10.dp))
                Text(state.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(20.dp)) {
        Text(message, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun stateColor(state: DownloadState?): Color = when (state) {
    DownloadState.Ready -> MaterialTheme.colorScheme.secondary
    is DownloadState.Failed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private fun stateLabel(state: DownloadState?): String = when (state) {
    is DownloadState.Downloading -> "Downloading"
    DownloadState.Verifying -> "Verifying"
    DownloadState.Ready -> "Ready ✓"
    is DownloadState.Failed -> "Needs attention"
    DownloadState.Queued -> "Waiting"
    null -> "Preparing"
}

private fun formatSize(bytes: Long): String = if (bytes >= 1_073_741_824L) {
    "%.1f GB".format(bytes / 1_073_741_824.0)
} else {
    "%.0f MB".format(bytes / 1_048_576.0)
}
