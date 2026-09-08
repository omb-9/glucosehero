package com.omb9.glucosehero.ui.stats

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.domain.model.Ea1cConfidence
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.TimeRange
import com.omb9.glucosehero.ui.components.GlucoseHeroRefreshIndicator
import com.omb9.glucosehero.ui.insights.MoodImpactSection
import com.omb9.glucosehero.ui.insights.TagImpactCard
import com.omb9.glucosehero.ui.insights.TagImpactUi
import com.omb9.glucosehero.ui.stats.components.GlucoseChart
import com.omb9.glucosehero.ui.stats.components.TimeInRangeBar
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import com.omb9.glucosehero.ui.theme.GlucoseHigh
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.RangeCategory
import com.omb9.glucosehero.util.TagImpactCopy
import kotlin.math.abs

private val InsightCardBackground = Color(0xFF000000)
private val InsightCardTitle = Color(0xFFFFFFFF)
private val InsightCardDescription = Color(0xFF9E9EA4)
private val InsightCardOutline = Color(0xFF3A3A3E)
private val InsightStandardAccent = Color(0xFF9E9EA4)
private val HighSeverityAccent = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onEntryClick: (Long) -> Unit,
    onSeeAllFoodImpact: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val enabledCategories by viewModel.enabledMarkerCategories.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreakDays.collectAsStateWithLifecycle()
    val supplies by viewModel.activeSupplies.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val weeklySummaryState by viewModel.weeklySummaryState.collectAsStateWithLifecycle()
    val foodImpactTags by viewModel.foodImpactTags.collectAsStateWithLifecycle()
    val moodImpactTags by viewModel.moodImpactTags.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val markerPopup by viewModel.selectedMarkerPopup.collectAsStateWithLifecycle()
    var showExportSheet by remember { mutableStateOf(false) }
    var showSupplySheet by remember { mutableStateOf(false) }
    var editingSupply by remember { mutableStateOf<ActiveSupplyUi?>(null) }
    var preselectedSupplyType by remember { mutableStateOf<SupplyType?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.exportEvents.collect { event ->
            when (event) {
                is ExportEvent.Ready -> {
                    showExportSheet = false
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file.file,
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.file.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share clinical report"))
                }
                is ExportEvent.Failed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats", style = MaterialTheme.typography.headlineMedium) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share clinical report",
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {
                GlucoseHeroRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.loadFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "Couldn't load glucose data. Showing the last available values.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            StreakIndicator(
                streakDays = streak,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            ActiveSuppliesSection(
                supplies = supplies,
                use24HourTime = state.use24HourTime,
                onAdd = {
                    preselectedSupplyType = null
                    showSupplySheet = true
                },
                onReplace = { type ->
                    preselectedSupplyType = type
                    showSupplySheet = true
                },
                onEdit = { supply -> editingSupply = supply },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            InsightsSection(
                insights = insights,
                weeklySummaryState = weeklySummaryState,
                onGenerateWeeklySummary = viewModel::generateWeeklySummary,
                onDismissWeeklySummary = viewModel::dismissWeeklySummary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            FoodImpactSection(
                tags = foodImpactTags,
                onSeeAll = onSeeAllFoodImpact,
                modifier = Modifier.fillMaxWidth(),
            )

            if (moodImpactTags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                MoodImpactSection(
                    moods = moodImpactTags,
                    onSeeAll = onSeeAllFoodImpact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            EstimatedA1cCard(
                state = state,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Glucose trend (${state.unit.label})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    RangePresetRow(
                        selected = state.range,
                        onSelect = viewModel::selectRange,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.hasData) {
                        MarkerFilterRow(
                            enabledCategories = enabledCategories,
                            onToggle = viewModel::toggleMarkerCategory,
                        )
                        Spacer(Modifier.height(12.dp))
                        TimeInRangeBar(
                            segments = state.tirSegments,
                            themeMode = state.themeMode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Log glucose readings to see your trend",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Stat cards ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Average",
                    value = state.avgDisplay,
                    suffix = state.unit.label,
                    trend = state.avgTrend,
                    delta = state.avgDeltaDisplay,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Readings",
                    value = Formatters.count(state.manualReadingCount),
                    suffix = null,
                    secondary = if (state.cgmReadingCount > 0) {
                        "+ ${Formatters.count(state.cgmReadingCount)} CGM samples"
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            StatCard(
                label = "Min / max",
                value = state.minMaxDisplay,
                suffix = null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
        }
    }

    if (showSupplySheet) {
        LogSupplySheet(
            preselectedType = preselectedSupplyType,
            onDismiss = { showSupplySheet = false },
            onSave = { type, days ->
                viewModel.logSupply(type, days)
                showSupplySheet = false
            },
        )
    }

    editingSupply?.let { supply ->
        EditSupplySheet(
            supply = supply,
            use24HourTime = state.use24HourTime,
            onDismiss = { editingSupply = null },
            onSave = { type, startedAt, days ->
                viewModel.updateSupply(supply.id, type, startedAt, days)
                editingSupply = null
            },
            onDelete = {
                viewModel.deleteSupply(supply.id)
                editingSupply = null
            },
        )
    }

    if (showExportSheet) {
        ExportFormatSheet(
            isExporting = isExporting,
            onSelect = { format -> viewModel.export(format) },
            onDismiss = { showExportSheet = false },
        )
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

@Composable
private fun MarkerFilterRow(
    enabledCategories: Set<MarkerCategory>,
    onToggle: (MarkerCategory) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkerCategory.entries.forEach { category ->
            FilterChip(
                selected = category in enabledCategories,
                onClick = { onToggle(category) },
                label = { Text(category.label) },
            )
        }
    }
}

@Composable
private fun RangePresetRow(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun StreakIndicator(
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(7) { index ->
                    val lit = index < streakDays
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (lit) accent else accent.copy(alpha = 0.14f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = when {
                        streakDays <= 0 -> "No streak yet"
                        streakDays == 1 -> "1 Day Streak"
                        else -> "$streakDays Day Streak"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Consecutive daily logs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightsSection(
    insights: List<InsightCardEntity>,
    weeklySummaryState: WeeklySummaryState,
    onGenerateWeeklySummary: () -> Unit,
    onDismissWeeklySummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        // "Generate Weekly Summary" button at the top of the Insights section
        Surface(
            onClick = onGenerateWeeklySummary,
            enabled = weeklySummaryState !is WeeklySummaryState.Loading,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                if (weeklySummaryState is WeeklySummaryState.Loading) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    GlucoseHigh.copy(alpha = 0.5f)
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (weeklySummaryState is WeeklySummaryState.Loading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        GlucoseHigh
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (weeklySummaryState is WeeklySummaryState.Loading) {
                        "Generating Weekly Summary…"
                    } else {
                        "Generate Weekly Summary"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (weeklySummaryState is WeeklySummaryState.Loading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        // Dedicated, dismissible weekly summary card
        when (weeklySummaryState) {
            is WeeklySummaryState.Loading -> {
                Spacer(Modifier.height(12.dp))
                WeeklySummaryLoadingCard(modifier = Modifier.fillMaxWidth())
            }

            is WeeklySummaryState.Success -> {
                Spacer(Modifier.height(12.dp))
                WeeklySummaryCard(
                    summary = weeklySummaryState.summary,
                    onDismiss = onDismissWeeklySummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is WeeklySummaryState.Error -> {
                Spacer(Modifier.height(12.dp))
                WeeklySummaryErrorCard(
                    message = weeklySummaryState.message,
                    onDismiss = onDismissWeeklySummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is WeeklySummaryState.Idle -> { /* Card is hidden / dismissed */ }
        }

        Spacer(Modifier.height(12.dp))

        if (insights.isEmpty()) {
            InsightsEmptyState()
        } else {
            // Rendered as a plain Column (not LazyColumn) so the cards simply
            // participate in the screen's existing verticalScroll. The worker
            // caps this list, so a lazy container would only add a nested
            // scrolling surface with no measurable benefit.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                insights.forEach { insight ->
                    InsightCard(insight = insight)
                }
            }
        }
    }
}

@Composable
private fun rememberOrangeShimmerBrush(): Brush {
    val shimmerColors = listOf(
        GlucoseHigh.copy(alpha = 0.08f),
        GlucoseHigh.copy(alpha = 0.28f),
        GlucoseHigh.copy(alpha = 0.08f),
    )
    val transition = rememberInfiniteTransition(label = "weekly-summary-orange-shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "weekly-summary-orange-shimmer-translate",
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f),
    )
}

@Composable
private fun WeeklySummaryLoadingCard(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberOrangeShimmerBrush()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, GlucoseHigh.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GlucoseHigh.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = GlucoseHigh,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly AI Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Analyzing 7-day glucose metrics & tags…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = GlucoseHigh,
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush),
            )
        }
    }
}

@Composable
private fun WeeklySummaryCard(
    summary: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, GlucoseHigh.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GlucoseHigh.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = GlucoseHigh,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly AI Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Past 7 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss summary",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WeeklySummaryErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Could not generate summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss error",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightCard(
    insight: InsightCardEntity,
    modifier: Modifier = Modifier,
) {
    val isHighSeverity = insight.severityLevel >= InsightCardEntity.SEVERITY_CRITICAL
    val accent = if (isHighSeverity) HighSeverityAccent else InsightStandardAccent

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = InsightCardBackground,
        border = BorderStroke(1.dp, InsightCardOutline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = accent.copy(alpha = 0.14f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = insightIcon(insight),
                    contentDescription = insightIconDescription(insight),
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = InsightCardTitle,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = InsightCardDescription,
                )
            }
        }
    }
}

@Composable
private fun InsightsEmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = InsightCardBackground,
        border = BorderStroke(1.dp, InsightCardOutline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = InsightStandardAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Gathering baseline data. Your insights will appear here after a few days of logging.",
                style = MaterialTheme.typography.bodyMedium,
                color = InsightCardDescription,
            )
        }
    }
}

private fun insightIcon(insight: InsightCardEntity): ImageVector = when {
    insight.title.contains("low", ignoreCase = true) -> Icons.Filled.ArrowDownward
    insight.title.contains("high", ignoreCase = true) -> Icons.Filled.ArrowUpward
    else -> Icons.Filled.Info
}

private fun insightIconDescription(insight: InsightCardEntity): String = when {
    insight.title.contains("low", ignoreCase = true) -> "Glucose trending down"
    insight.title.contains("high", ignoreCase = true) -> "Glucose trending up"
    else -> "Insight"
}

@Composable
private fun FoodImpactSection(
    tags: List<TagImpactUi>,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxAbsDelta = remember(tags) {
        tags.maxOfOrNull { abs(it.medianDeltaMgdl) } ?: 0.0
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Food impact",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) {
                Text("See all")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (tags.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = TagImpactCopy.emptyFoodImpact(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tags.forEach { tag ->
                    TagImpactCard(
                        item = tag,
                        maxAbsDelta = maxAbsDelta,
                        onShare = null,
                        onDismiss = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun EstimatedA1cCard(
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Estimated A1c",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showInfo = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "About Estimated A1c",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.ea1cConfidence == Ea1cConfidence.INSUFFICIENT_DATA) {
                Text(
                    text = insufficientDataMessage(
                        neededDays = state.ea1cNeededDays,
                        neededReadings = state.ea1cNeededReadings,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.ea1cValue,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state.ea1cConfidence) {
                        Ea1cConfidence.FULL_90_DAY_WINDOW -> "Full 90-day window"
                        else -> "Building estimate"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Got it")
                }
            },
            title = { Text("About Estimated A1c") },
            text = {
                Text(
                    "Estimated A1c (eA1c) is calculated from your logged blood " +
                        "glucose over a 90-day window using the ADAG formula. The more " +
                        "consistently you log, the closer this estimate matches a clinical " +
                        "lab test. Consult your doctor for medical decisions."
                )
            },
        )
    }
}

private fun insufficientDataMessage(neededDays: Int, neededReadings: Int): String {
    val dayPart = when {
        neededDays <= 0 -> null
        neededDays == 1 -> "1 more day"
        else -> "$neededDays more days"
    }
    val readingPart = when {
        neededReadings <= 0 -> null
        neededReadings == 1 -> "1 more reading"
        else -> "$neededReadings more readings"
    }
    return when {
        dayPart != null && readingPart != null ->
            "Log $dayPart or $readingPart to generate your estimate."
        dayPart != null -> "Log $dayPart of glucose data to generate your estimate."
        readingPart != null -> "Log $readingPart to generate your estimate."
        else -> "Log glucose readings to generate your estimate."
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    suffix: String?,
    modifier: Modifier = Modifier,
    trend: TrendDirection = TrendDirection.UNKNOWN,
    delta: String? = null,
    secondary: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (suffix != null) {
                    Spacer(Modifier.height(0.dp))
                    Text(
                        " $suffix",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (trend != TrendDirection.UNKNOWN && delta != null) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        Text(
                            trendArrow(trend),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            delta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (secondary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun trendArrow(trend: TrendDirection): String = when (trend) {
    TrendDirection.UP -> "↑"
    TrendDirection.DOWN -> "↓"
    TrendDirection.FLAT -> "→"
    TrendDirection.UNKNOWN -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatSheet(
    isExporting: Boolean,
    onSelect: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Share clinical report",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (isExporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Preparing report…")
                }
            }
            ExportOptionRow(
                title = "Clinical report (PDF)",
                enabled = !isExporting,
                onClick = { onSelect(ExportFormat.PDF) },
            )
            ExportOptionRow(
                title = "Clinical report (CSV)",
                enabled = !isExporting,
                onClick = { onSelect(ExportFormat.CSV) },
            )
        }
    }
}

@Composable
private fun ExportOptionRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun InsightCardPreview() {
    GlucoseHeroTheme {
        Column(
            modifier = Modifier
                .background(InsightCardBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InsightCard(
                insight = InsightCardEntity(
                    title = "Frequent overnight lows",
                    description = "Glucose dropped below 70 mg/dL 3 times between " +
                        "2 AM and 4 AM over the last 14 days.",
                    severityLevel = InsightCardEntity.SEVERITY_CRITICAL,
                    createdAt = 1_700_000_000_000L,
                )
            )
            InsightCard(
                insight = InsightCardEntity(
                    title = "Recurring high around 6 PM",
                    description = "Average glucose was 198 mg/dL during the " +
                        "6 PM hour over the last 14 days.",
                    severityLevel = InsightCardEntity.SEVERITY_WARNING,
                    createdAt = 1_700_000_000_000L,
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsEmptyStatePreview() {
    GlucoseHeroTheme {
        Box(
            modifier = Modifier
                .background(InsightCardBackground)
                .padding(16.dp),
        ) {
            InsightsEmptyState()
        }
    }
}

private val MarkerPopupBackground = Color(0xFF000000)
private val MarkerPopupSurface = Color(0xFF0A0A0B)
private val MarkerPopupTextPrimary = Color(0xFFFFFFFF)
private val MarkerPopupTextSecondary = Color(0xFF9E9EA4)
private val MarkerPopupOutline = Color(0xFF3A3A3E)
private val MarkerPopupDivider = Color(0xFF1E1E22)
private val MarkerPopupIconBg = Color(0xFF161618)
private val MarkerPopupOrangeAccent = Color(0xFFFF7043)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerDetailBottomSheet(
    entry: MarkerPopupUiState,
    onDismiss: () -> Unit,
    onViewFullDetails: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MarkerPopupBackground,
        contentColor = MarkerPopupTextPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MarkerPopupOutline,
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Entry Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MarkerPopupTextPrimary,
                    )
                    Text(
                        text = "${entry.timeDisplay} · ${entry.dateDisplay}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarkerPopupTextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MarkerPopupTextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Primary Data Card container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MarkerPopupSurface,
                border = BorderStroke(1.dp, MarkerPopupOutline),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 1. Time
                    MarkerDataRow(
                        icon = Icons.Filled.Schedule,
                        isActive = true,
                        label = "TIME",
                        primaryValue = "${entry.timeDisplay} · ${entry.dateDisplay}",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MarkerPopupDivider),
                    )

                    // 2. Glucose
                    val glucoseText = if (entry.hasGlucose && entry.glucoseDisplay != null) {
                        "${entry.glucoseDisplay} ${entry.glucoseUnitLabel}"
                    } else {
                        "No reading"
                    }
                    val glucoseSecondary = if (entry.hasGlucose && entry.glucoseRange != null) {
                        when (entry.glucoseRange) {
                            RangeCategory.VERY_LOW -> "Very Low"
                            RangeCategory.LOW -> "Low"
                            RangeCategory.IN_RANGE -> "In Range"
                            RangeCategory.HIGH -> "High"
                            RangeCategory.VERY_HIGH -> "Very High"
                        }
                    } else {
                        null
                    }
                    MarkerDataRow(
                        icon = Icons.Filled.Bloodtype,
                        isActive = entry.hasGlucose,
                        label = "GLUCOSE",
                        primaryValue = glucoseText,
                        secondaryValue = glucoseSecondary,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MarkerPopupDivider),
                    )

                    // 3. Food
                    val foodPrimary = when {
                        entry.foodDescription != null -> entry.foodDescription
                        entry.carbsDisplay != null -> entry.carbsDisplay
                        else -> "None logged"
                    }
                    val foodSecondary = if (entry.foodDescription != null && entry.carbsDisplay != null) {
                        entry.carbsDisplay
                    } else {
                        null
                    }
                    MarkerDataRow(
                        icon = Icons.Filled.Restaurant,
                        isActive = entry.hasFood,
                        label = "FOOD",
                        primaryValue = foodPrimary,
                        secondaryValue = foodSecondary,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MarkerPopupDivider),
                    )

                    // 4. Insulin
                    val insulinText = if (entry.hasInsulin && entry.insulinDisplay != null) {
                        entry.insulinDisplay
                    } else {
                        "None logged"
                    }
                    MarkerDataRow(
                        icon = Icons.Filled.Vaccines,
                        isActive = entry.hasInsulin,
                        label = "INSULIN",
                        primaryValue = insulinText,
                    )

                    // 5. Note (if present)
                    if (!entry.note.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MarkerPopupDivider),
                        )
                        MarkerDataRow(
                            icon = Icons.Filled.Notes,
                            isActive = true,
                            label = "NOTE",
                            primaryValue = entry.note,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Full Entry Routing Button
            Button(
                onClick = { onViewFullDetails(entry.entryId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarkerPopupOrangeAccent,
                    contentColor = Color.White,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "View Full Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkerDataRow(
    icon: ImageVector,
    isActive: Boolean,
    label: String,
    primaryValue: String,
    secondaryValue: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MarkerPopupIconBg,
            border = BorderStroke(
                1.dp,
                if (isActive) MarkerPopupOrangeAccent.copy(alpha = 0.4f) else MarkerPopupOutline,
            ),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) MarkerPopupOrangeAccent else MarkerPopupTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MarkerPopupTextSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MarkerPopupTextPrimary else MarkerPopupTextSecondary,
                )
                if (secondaryValue != null) {
                    Text(
                        text = secondaryValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarkerPopupTextSecondary,
                    )
                }
            }
        }
    }
}
