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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemDialog(
    onDismiss: () -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialAttachmentUri: Uri? = null,
    onSubmitted: (BugReport) -> Unit = {},
    viewModel: FeedbackViewModel = viewModel(),
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var contactName by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var attachmentUri by remember(initialAttachmentUri) { mutableStateOf(initialAttachmentUri) }
    var submitting by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }
    var userMessage by remember { mutableStateOf<String?>(null) }

    val configError = remember { viewModel.configError() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        attachmentUri = uri
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
            Text("Report a Problem", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            WarningCard()

            configError?.let {
                Text(
                    "Configuration issue: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            userError?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            userMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Subject *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeDiagnostics, onCheckedChange = { includeDiagnostics = it })
                Text("Include app/device diagnostics", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contactEmail,
                onValueChange = { contactEmail = it },
                label = { Text("Email (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            attachmentUri?.let { uri ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected screenshot",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { attachmentUri = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Remove attachment") }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (attachmentUri == null) "Attach screenshot" else "Replace")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        submitting = true
                        userError = null
                        userMessage = null
                        viewModel.submitReport(
                            title = title,
                            description = description,
                            contactName = contactName,
                            contactEmail = contactEmail,
                            includeDiagnostics = includeDiagnostics,
                            attachmentUri = attachmentUri,
                        ) { result ->
                            submitting = false
                            when (result) {
                                is FeedbackResult.Success -> {
                                    onSubmitted(result.value)
                                    userMessage = "Report #${result.value.number} submitted. Thank you!"
                                    title = ""
                                    description = ""
                                    contactName = ""
                                    contactEmail = ""
                                    attachmentUri = null
                                    onDismiss()
                                }
                                is FeedbackResult.Failure -> userError = result.userMessage
                            }
                        }
                    },
                    enabled = !submitting && configError == null && title.isNotBlank() && description.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = "Your report will be submitted to this app's GitHub issue tracker. Do not include passwords, private keys, medical information, financial information, or anything you do not want visible to the repository maintainers. If this repository is public, your report may be publicly visible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
