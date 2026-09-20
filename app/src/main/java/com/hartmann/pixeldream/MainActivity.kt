package com.hartmann.pixeldream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hartmann.pixeldream.ads.ConsentManager
import com.google.android.gms.ads.MobileAds
import com.hartmann.pixeldream.onboarding.OnboardingNavGraph
import com.hartmann.pixeldream.requirements.DeviceRequirements
import com.hartmann.pixeldream.requirements.RequirementReport
import com.hartmann.pixeldream.ui.theme.PixelDreamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ConsentManager.requestConsentIfNeeded(this) { canRequestAds ->
            if (canRequestAds) {
                MobileAds.initialize(this) { runOnUiThread { adsReady = true } }
            }
        }
        setContent {
            PixelDreamTheme {
                val preferences = remember { getSharedPreferences("app_state", MODE_PRIVATE) }
                var onboardingComplete by remember {
                    mutableStateOf(
                        preferences.getBoolean("onboarding_complete", false) &&
                            preferences.getInt("model_catalog_version", 0) == MODEL_CATALOG_VERSION,
                    )
                }
                var requirementsReport by remember { mutableStateOf<RequirementReport?>(null) }
                var showRequirementsDialog by remember { mutableStateOf(false) }
                // Collecting specs performs blocking I/O (ActivityManager, StatFs), so it runs
                // off the main thread; the dialog appears once the result is in.
                LaunchedEffect(Unit) {
                    val report = withContext(Dispatchers.IO) {
                        DeviceRequirements.evaluateDevice(this@MainActivity)
                    }
                    requirementsReport = report
                    showRequirementsDialog = !report.meetsMinimum &&
                        !preferences.getBoolean("requirements_warning_dismissed", false)
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (onboardingComplete) {
                        MainNavGraph(adsReady = adsReady)
                    } else {
                        OnboardingNavGraph(
                            onOnboardingComplete = {
                                preferences.edit()
                                    .putBoolean("onboarding_complete", true)
                                    .putInt("model_catalog_version", MODEL_CATALOG_VERSION)
                                    .apply()
                                onboardingComplete = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                    val report = requirementsReport
                    if (showRequirementsDialog && report != null) {
                        RequirementsWarningDialog(
                            report = report,
                            onConfirm = { dontShowAgain ->
                                preferences.edit()
                                    .putBoolean("requirements_warning_dismissed", dontShowAgain)
                                    .apply()
                                showRequirementsDialog = false
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MODEL_CATALOG_VERSION = 3
    }
}

@Composable
private fun RequirementsWarningDialog(
    report: RequirementReport,
    onConfirm: (dontShowAgain: Boolean) -> Unit,
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onConfirm(dontShowAgain) },
        title = { Text("This device is below the minimum specs") },
        text = {
            Column {
                Text("PixelDream runs AI image generation entirely on your phone. This device is below the minimum recommended specs, so images may take much longer to generate and quality may be lower:")
                Spacer(Modifier.height(8.dp))
                report.checks.filter { !it.passed }.forEach { check ->
                    Text("• ${check.label}: ${check.actual} (${check.required} required)")
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                    )
                    Text(
                        "Don't show again",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontShowAgain) }) {
                Text("Got it")
            }
        },
    )
}
