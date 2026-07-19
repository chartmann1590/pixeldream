package com.hartmann.pixeldream.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hartmann.pixeldream.model.DeviceTier
import com.hartmann.pixeldream.model.DownloadState
import com.hartmann.pixeldream.model.ModelDescriptor
import com.hartmann.pixeldream.ui.components.GenerativeProgressIndicator
import com.hartmann.pixeldream.ui.components.PixelDreamButton

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PixelDream", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Turn a rough idea into an image, entirely on your phone. " +
                "No account, no cloud, no waiting on a server.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        PixelDreamButton(text = "Get started", onClick = onContinue)
    }
}

@Composable
fun ContentPolicyScreen(onAgree: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Before you start", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "PixelDream generates images from your prompts using on-device AI. " +
                    "Please don't use it to create content that sexualizes minors, " +
                    "harasses a real person, or depicts extreme violence. Prompts are " +
                    "checked automatically, and you can report any image from its " +
                    "full-screen view.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PixelDreamButton(text = "I agree, continue", onClick = onAgree)
    }
}

@Composable
fun DeviceCheckScreen(
    deviceTier: DeviceTier?,
    totalRamMb: Int,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Your device", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            val ramGb = totalRamMb / 1024.0
            when (deviceTier) {
                DeviceTier.RECOMMENDED -> Text(
                    "You have about ${"%.1f".format(ramGb)} GB of RAM — generation should run smoothly.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                DeviceTier.LOW -> Text(
                    "You have about ${"%.1f".format(ramGb)} GB of RAM. PixelDream will still work, " +
                        "but generation may take longer and Low-power mode will be enabled by default.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                null -> Text("Checking your device...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        PixelDreamButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
fun ModelDownloadScreen(
    models: List<ModelDescriptor>,
    downloadStates: Map<com.hartmann.pixeldream.model.ModelKind, DownloadState>,
    manifestError: String?,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    allReady: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text("Downloading your models", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            GenerativeProgressIndicator()
            Spacer(Modifier.height(24.dp))

            if (manifestError != null) {
                Text(
                    "Couldn't reach the model server: $manifestError",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (models.isEmpty()) {
                Text("Getting ready to download...", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(models) { descriptor ->
                        val state = downloadStates[descriptor.kind]
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(descriptor.kind.name, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            when (state) {
                                is DownloadState.Downloading -> {
                                    val progress = if (state.totalBytes > 0) {
                                        state.bytesDownloaded.toFloat() / state.totalBytes
                                    } else {
                                        0f
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                DownloadState.Verifying -> Text("Verifying...")
                                DownloadState.Ready -> Text("Ready")
                                is DownloadState.Failed -> Text("Failed: ${state.reason}")
                                DownloadState.Queued, null -> Text("Queued")
                            }
                        }
                    }
                }
            }
        }

        if (manifestError != null) {
            PixelDreamButton(text = "Retry", onClick = onStart)
        } else if (models.isEmpty()) {
            PixelDreamButton(text = "Start download", onClick = onStart)
        } else if (allReady) {
            PixelDreamButton(text = "Start creating", onClick = onContinue)
        }
    }
}
