package com.omb9.glucosehero.ui.stats

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.ui.stats.components.ExpandedMarkerPointSize
import com.omb9.glucosehero.ui.stats.components.GlucoseChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseChartFullscreenScreen(
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    UnlockLandscapeWhileVisible()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabledCategories by viewModel.enabledMarkerCategories.collectAsStateWithLifecycle()
    val markerPopup by viewModel.selectedMarkerPopup.collectAsStateWithLifecycle()
    val trendTitle = stringResource(R.string.stats_glucose_trend_title, state.unit.label)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trendTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_chart_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            RangePresetRow(
                selected = state.range,
                onSelect = viewModel::selectRange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (state.hasData) {
                GlucoseChart(
                    points = state.chartPoints,
                    markers = state.markers,
                    enabledCategories = enabledCategories,
                    targetLow = state.targetLowDisplay,
                    targetHigh = state.targetHighDisplay,
                    veryLowThreshold = state.veryLowThresholdDisplay,
                    veryHighThreshold = state.veryHighThresholdDisplay,
                    minY = state.chartMinY,
                    maxY = state.chartMaxY,
                    rangeDays = state.range.days,
                    rangeStartMillis = state.rangeStartMillis,
                    themeMode = state.themeMode,
                    use24Hour = state.use24HourTime,
                    onMarkerClick = viewModel::selectMarker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    fillMaxHeight = true,
                    pinchZoomEnabled = true,
                    markerPointSize = ExpandedMarkerPointSize,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stats_chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    markerPopup?.let { popup ->
        MarkerDetailBottomSheet(
            entry = popup,
            onDismiss = viewModel::dismissMarkerPopup,
            onViewFullDetails = { entryId ->
                viewModel.dismissMarkerPopup()
                onEntryClick(entryId)
            },
        )
    }
}

/**
 * MainActivity is not portrait-locked in the manifest, but this route still requests
 * [ActivityInfo.SCREEN_ORIENTATION_FULL_USER] so landscape (including reverse) is allowed
 * if a future lock is added. Restore the previous orientation when leaving, except during
 * configuration change so rotation does not snap back to portrait mid-chart.
 */
@Composable
private fun UnlockLandscapeWhileVisible() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        if (activity == null) {
            onDispose { }
        } else {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            onDispose {
                if (!activity.isChangingConfigurations) {
                    activity.requestedOrientation = previous
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
