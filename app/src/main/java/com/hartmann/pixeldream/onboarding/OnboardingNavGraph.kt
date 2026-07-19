package com.hartmann.pixeldream.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object OnboardingRoute {
    const val WELCOME = "welcome"
    const val CONTENT_POLICY = "content_policy"
    const val DEVICE_CHECK = "device_check"
    const val MODEL_DOWNLOAD = "model_download"
}

@Composable
fun OnboardingNavGraph(onOnboardingComplete: () -> Unit, modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    val viewModel: OnboardingViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = OnboardingRoute.WELCOME, modifier = modifier) {
        composable(OnboardingRoute.WELCOME) {
            WelcomeScreen(onContinue = { navController.navigate(OnboardingRoute.CONTENT_POLICY) })
        }
        composable(OnboardingRoute.CONTENT_POLICY) {
            ContentPolicyScreen(onAgree = { navController.navigate(OnboardingRoute.DEVICE_CHECK) })
        }
        composable(OnboardingRoute.DEVICE_CHECK) {
            DeviceCheckScreen(
                deviceTier = uiState.deviceTier,
                totalRamMb = uiState.totalRamMb,
                onContinue = { navController.navigate(OnboardingRoute.MODEL_DOWNLOAD) },
            )
        }
        composable(OnboardingRoute.MODEL_DOWNLOAD) {
            ModelDownloadScreen(
                models = uiState.models,
                downloadStates = uiState.downloadStates,
                manifestError = uiState.manifestError,
                onStart = viewModel::loadManifestAndStartDownloads,
                onContinue = onOnboardingComplete,
                allReady = viewModel.allModelsReady(),
            )
        }
    }
}
