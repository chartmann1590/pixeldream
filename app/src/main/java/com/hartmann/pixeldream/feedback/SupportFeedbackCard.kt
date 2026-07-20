package com.hartmann.pixeldream.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable card that hosts the "Support & Feedback" section: a button to file
 * a new report plus the list of locally submitted reports. Tap a report to open
 * the issue details / comments dialog.
 */
@Composable
fun SupportFeedbackCard(
    modifier: Modifier = Modifier,
    viewModel: FeedbackViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var openedReport by rememberSaveable { mutableStateOf<BugReport?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Support & Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Report bugs and track status. Reports are filed to GitHub Issues for this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showReportDialog = true },
                enabled = state.isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isConfigured) "Report a Problem" else (state.configError ?: "Feedback unavailable"))
            }

            if (state.reports.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Your submitted reports", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.reports.forEach { report ->
                    ReportRow(report = report, onClick = { openedReport = report })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            confirmButton = {},
            text = { ReportProblemDialog(onDismiss = { showReportDialog = false }) },
        )
    }

    openedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { openedReport = null },
            confirmButton = {},
            text = { IssueDetailsDialog(report = report, onDismiss = { openedReport = null }) },
        )
    }
}

@Composable
private fun ReportRow(report: BugReport, onClick: () -> Unit) {
    val isOpen = report.status.equals("open", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${report.number}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    report.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(state = report.status)
            }
            Text(
                "Opened ${formatIso(report.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(state: String) {
    val isOpen = state.equals("open", ignoreCase = true)
    AssistChip(
        onClick = {},
        label = { Text(state.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            labelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

private fun formatIso(iso: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(iso) ?: Date()
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsed)
}.getOrDefault(iso)
