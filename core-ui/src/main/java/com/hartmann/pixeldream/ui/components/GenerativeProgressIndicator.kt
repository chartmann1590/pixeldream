package com.hartmann.pixeldream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * A slow-rotating gradient ring used to make the two longest waits in the app
 * (model download, image generation) feel alive rather than dead time.
 */
@Composable
fun GenerativeProgressIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "generativePulse")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "rotation",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier.size(96.dp)) {
        rotate(rotation) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(primary, secondary, primary),
                    center = Offset(size.width / 2, size.height / 2),
                ),
                radius = size.minDimension / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f),
            )
        }
    }
}
