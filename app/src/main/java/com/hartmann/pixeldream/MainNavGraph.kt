package com.hartmann.pixeldream

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hartmann.pixeldream.gallery.GalleryScreen
import com.hartmann.pixeldream.gallery.GenerationViewerScreen
import com.hartmann.pixeldream.generation.GenerationScreen

private object MainRoute {
    const val GENERATE = "generate"
    const val GALLERY = "gallery"
    const val VIEWER = "viewer/{generationId}"
    fun viewer(id: String) = "viewer/$id"
}

@Composable
fun MainNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == MainRoute.GENERATE } == true,
                    onClick = {
                        navController.navigate(MainRoute.GENERATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {},
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
                    icon = {},
                    label = { Text("Gallery") },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoute.GENERATE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MainRoute.GENERATE) { GenerationScreen() }
            composable(MainRoute.GALLERY) {
                GalleryScreen(onOpenGeneration = { navController.navigate(MainRoute.viewer(it.id)) })
            }
            composable(
                MainRoute.VIEWER,
                arguments = listOf(navArgument("generationId") {}),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("generationId").orEmpty()
                GenerationViewerScreen(initialGenerationId = id, onClose = { navController.popBackStack() })
            }
        }
    }
}
