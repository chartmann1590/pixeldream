package com.hartmann.pixeldream

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.saveable.rememberSaveable
import com.hartmann.pixeldream.billing.BillingRepository
import com.hartmann.pixeldream.ads.BannerAd
import com.hartmann.pixeldream.billing.PaywallScreen
import com.hartmann.pixeldream.gallery.GalleryScreen
import com.hartmann.pixeldream.gallery.GenerationViewerScreen
import com.hartmann.pixeldream.generation.GenerationScreen
import com.hartmann.pixeldream.settings.SettingsScreen

private object MainRoute {
    const val GENERATE = "generate"
    const val GALLERY = "gallery"
    const val VIEWER = "viewer"
    const val PAYWALL = "paywall"
    const val SETTINGS = "settings"
}

@Composable
fun MainNavGraph(adsReady: Boolean) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val billingRepository = remember { BillingRepository(context) }
    val isAdFree by billingRepository.isAdFree.collectAsState(initial = false)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectedGenerationId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var editPrompt by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    LaunchedEffect(billingRepository) { billingRepository.refreshEntitlement() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentDestination?.route != MainRoute.VIEWER && currentDestination?.route != MainRoute.PAYWALL) Column {
                if (adsReady && !isAdFree) BannerAd()
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == MainRoute.GENERATE } == true,
                    onClick = {
                        navController.navigate(MainRoute.GENERATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("✦", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Create") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == MainRoute.GALLERY } == true,
                    onClick = {
                        navController.navigate(MainRoute.GALLERY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("▦", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Gallery") },
                )
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == MainRoute.SETTINGS } == true,
                    onClick = {
                        navController.navigate(MainRoute.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text("⚙", style = MaterialTheme.typography.titleMedium) },
                    label = { Text("Settings") },
                )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoute.GENERATE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MainRoute.GENERATE) {
                GenerationScreen(
                    adsReady = adsReady && !isAdFree,
                    initialPrompt = editPrompt,
                    onInitialPromptConsumed = { editPrompt = null },
                )
            }
            composable(MainRoute.GALLERY) {
                GalleryScreen(onOpenGeneration = {
                    selectedGenerationId = it.id
                    navController.navigate(MainRoute.VIEWER)
                })
            }
            composable(MainRoute.VIEWER) {
                GenerationViewerScreen(
                    initialGenerationId = selectedGenerationId.orEmpty(),
                    onClose = { navController.popBackStack() },
                    onEdit = { prompt ->
                        editPrompt = prompt
                        navController.navigate(MainRoute.GENERATE) {
                            popUpTo(MainRoute.GALLERY)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(MainRoute.PAYWALL) { PaywallScreen(billingRepository) }
            composable(MainRoute.SETTINGS) {
                SettingsScreen(onOpenAdFree = { navController.navigate(MainRoute.PAYWALL) })
            }
        }
    }
}
