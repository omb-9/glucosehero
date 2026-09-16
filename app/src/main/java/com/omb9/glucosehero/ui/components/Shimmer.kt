package com.omb9.glucosehero.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

private const val ShimmerDurationMillis = 1200

/**
 * Sweeping highlight for loading placeholders so skeleton shapes read as
 * in-progress rather than as empty or broken layout.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ShimmerDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    return drawWithCache {
        val width = size.width.coerceAtLeast(1f)
        val brush = Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = 0.08f),
                color.copy(alpha = 0.18f),
                color.copy(alpha = 0.08f),
            ),
            start = Offset(width * (translate - 1f), 0f),
            end = Offset(width * translate, 0f),
        )
        onDrawBehind { drawRect(brush) }
    }
}
