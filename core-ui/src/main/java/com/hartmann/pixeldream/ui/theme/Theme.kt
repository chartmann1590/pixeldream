package com.hartmann.pixeldream.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DreamDarkScheme = darkColorScheme(
    primary = DreamPink80,
    secondary = DreamCyan80,
    background = DreamSurfaceDark,
    surface = DreamSurfaceDark,
    surfaceVariant = Color(0xFF2B2438),
    onSurfaceVariant = Color(0xFFD0C6DC),
    primaryContainer = Color(0xFF552598),
    secondaryContainer = Color(0xFF164D4A),
    error = DreamError,
    onPrimary = DreamIndigo10,
    onBackground = DreamIndigo90,
    onSurface = DreamIndigo90,
)

private val DreamLightScheme = lightColorScheme(
    primary = DreamPink40,
    secondary = DreamCyan40,
    background = DreamSurfaceLight,
    surface = DreamSurfaceLight,
    surfaceVariant = Color(0xFFECE5F3),
    onSurfaceVariant = Color(0xFF625A69),
    primaryContainer = Color(0xFFEBDDFF),
    secondaryContainer = Color(0xFFB8F2EC),
    error = DreamError,
    onPrimary = DreamSurfaceLight,
    onBackground = DreamIndigo20,
    onSurface = DreamIndigo20,
)

/**
 * PixelDream's Material 3 theme. Dynamic color (Android 12+) is intentionally left
 * off by default so the app keeps its own brand identity rather than matching wallpaper.
 */
@Composable
fun PixelDreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DreamDarkScheme
        else -> DreamLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PixelDreamTypography,
        content = content,
    )
}
