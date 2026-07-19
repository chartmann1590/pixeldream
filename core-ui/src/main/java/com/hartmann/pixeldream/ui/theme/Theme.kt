package com.hartmann.pixeldream.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DreamDarkScheme = darkColorScheme(
    primary = DreamPink80,
    secondary = DreamCyan80,
    background = DreamSurfaceDark,
    surface = DreamSurfaceDark,
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
