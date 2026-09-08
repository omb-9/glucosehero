package com.omb9.glucosehero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val IndicatorMarkSize = 64.dp
private val IndicatorTopPadding = 40.dp
private const val ScrimAlpha = 0.12f
private const val ScrimFadeInMillis = 300
private const val ScrimFadeOutMillis = 500
private const val MarkFadeOutMillis = 450
private const val PulseDurationMillis = 1100
private const val LaunchOvershoot = 1.75f

/**
 * Shared pull-to-refresh indicator for the Log and Stats screens.
 *
 * While refreshing, a low-opacity scrim fades in behind the in-app logo mark
 * and the mark holds at the pull threshold with a gentle breathing pulse. On
 * completion the mark "launches" upward past its bounds with a spring so the
 * release has a little snap, while both the mark and scrim fade out.
 *
 * This composable is designed to be passed into the [indicator][androidx.compose.material3.pulltorefresh.PullToRefreshBox]
 * slot and positions itself from [PullToRefreshState.distanceFraction], so the
 * pull motion is identical wherever it is used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseHeroRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val launchDistancePx = with(density) { (IndicatorMarkSize * LaunchOvershoot).toPx() }
    val scrimColor = MaterialTheme.colorScheme.background

    val scrimAlpha = remember { Animatable(0f) }
    val markAlpha = remember { Animatable(0f) }
    val launchOffset = remember { Animatable(0f) }

    var launching by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }

    // Gentle breathing scale while the refresh is active.
    val pulse = rememberInfiniteTransition(label = "glucose-hero-refresh-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glucose-hero-refresh-pulse-scale",
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            wasRefreshing = true
            launching = false
            scrimAlpha.animateTo(ScrimAlpha, tween(durationMillis = ScrimFadeInMillis))
        } else if (wasRefreshing) {
            wasRefreshing = false
            markAlpha.snapTo(1f)
            launchOffset.snapTo(thresholdPx)
            launching = true

            coroutineScope {
                launch { scrimAlpha.animateTo(0f, tween(durationMillis = ScrimFadeOutMillis)) }
                launch { markAlpha.animateTo(0f, tween(durationMillis = MarkFadeOutMillis)) }
                launchOffset.animateTo(
                    targetValue = -launchDistancePx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }

            launching = false
            markAlpha.snapTo(0f)
            launchOffset.snapTo(0f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor.copy(alpha = scrimAlpha.value)),
        )

        Image(
            painter = painterResource(R.drawable.ic_logo_display),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = IndicatorTopPadding)
                .size(IndicatorMarkSize)
                .graphicsLayer {
                    val pull = state.distanceFraction.coerceIn(0f, 1f)

                    val alpha = when {
                        launching -> markAlpha.value
                        isRefreshing -> 1f
                        else -> pull
                    }
                    val scale = when {
                        launching -> 1f
                        isRefreshing -> pulseScale
                        else -> 0.7f + 0.3f * pull
                    }
                    val translationY = when {
                        launching -> launchOffset.value
                        isRefreshing -> thresholdPx
                        else -> thresholdPx * pull
                    }

                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                    this.translationY = translationY
                },
        )
    }
}
