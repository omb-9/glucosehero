package com.omb9.glucosehero.ui.stats

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.Ea1cConfidence
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.TimeRange
import com.omb9.glucosehero.util.Formatters
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.decoration.ThresholdLine
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.ShapeComponent
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreakDays.collectAsStateWithLifecycle()
    val supplies by viewModel.activeSupplies.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    var showExportSheet by remember { mutableStateOf(false) }
    var showSupplySheet by remember { mutableStateOf(false) }
    var preselectedSupplyType by remember { mutableStateOf<SupplyType?>(null) }
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
                    context.startActivity(Intent.createChooser(sendIntent, "Share report"))
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
                title = { Text("Stats") },
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Export report",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // --- 7/14/30/90-day range selector ---
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimeRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = state.range == range,
                        onClick = { viewModel.selectRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TimeRange.entries.size,
                        ),
                    ) {
                        Text(range.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            StreakIndicator(
                streakDays = streak,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            ActiveSuppliesSection(
                supplies = supplies,
                onAdd = {
                    preselectedSupplyType = null
                    showSupplySheet = true
                },
                onReplace = { type ->
                    preselectedSupplyType = type
                    showSupplySheet = true
                },
                modifier = Modifier.fillMaxWidth(),
            )

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
                    if (state.hasData) {
                        TrendChart(state = state, viewModel = viewModel)
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
                    label = "Time in range",
                    value = state.tirDisplay,
                    suffix = null,
                    trend = state.tirTrend,
                    delta = state.tirDeltaDisplay,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Readings",
                    value = state.readingCount.toString(),
                    suffix = null,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Min / max",
                    value = state.minMaxDisplay,
                    suffix = null,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
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

    if (showExportSheet) {
        ExportFormatSheet(
            isExporting = isExporting,
            onSelect = { format -> viewModel.export(format) },
            onDismiss = { showExportSheet = false },
        )
    }
}

@Composable
private fun ActiveSuppliesSection(
    supplies: ImmutableList<ActiveSupplyUi>,
    onAdd: () -> Unit,
    onReplace: (SupplyType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Active Supplies",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (supplies.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "No active supplies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAdd) {
                        Text("Log supply")
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(supplies, key = { it.id }) { supply ->
                    SupplyCard(
                        supply = supply,
                        onReplace = { onReplace(supply.type) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplyCard(
    supply: ActiveSupplyUi,
    onReplace: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.width(168.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = supply.type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Started ${Formatters.shortDate(Formatters.localDate(supply.startedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { supply.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.14f),
            )
            Spacer(Modifier.height(8.dp))
            if (supply.isExpired) {
                AssistChip(
                    onClick = onReplace,
                    label = { Text("Replace") },
                )
            } else {
                Text(
                    text = remainingLabel(supply),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSupplySheet(
    preselectedType: SupplyType?,
    onDismiss: () -> Unit,
    onSave: (SupplyType, Int) -> Unit,
) {
    var selectedType by remember(preselectedType) {
        mutableStateOf(preselectedType ?: SupplyType.SENSOR)
    }
    val lifespanOptions = remember(selectedType) { lifespanOptionsFor(selectedType) }
    var selectedDays by remember(selectedType) { mutableStateOf(lifespanOptions.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Log New Supply",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Type",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupplyType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Expected lifespan",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lifespanOptions.forEach { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { selectedDays = days },
                        label = { Text("$days days") },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(selectedType, selectedDays) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

private fun lifespanOptionsFor(type: SupplyType): List<Int> = when (type) {
    SupplyType.SENSOR -> listOf(10, 14)
    SupplyType.INSULIN_VIAL -> listOf(28)
    SupplyType.PUMP_SITE -> listOf(3)
}

private fun remainingLabel(supply: ActiveSupplyUi): String = when {
    supply.daysRemaining >= 1.0 -> "${ceil(supply.daysRemaining).toInt()} days left"
    supply.hoursRemaining > 0.0 -> "${ceil(supply.hoursRemaining).toInt()} hours left"
    else -> "Expired"
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
private fun TrendChart(state: StatsUiState, viewModel: StatsViewModel) {
    val accent = MaterialTheme.colorScheme.primary
    val shadeColor = accent.copy(alpha = 0.12f).toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Target-range shading: a ThresholdLine decoration spanning low..high in
    // the display unit, rebuilt only when the range bounds actually change.
    val thresholdLine = remember(state.targetLowDisplay, state.targetHighDisplay, shadeColor) {
        ThresholdLine(
            thresholdRange = state.targetLowDisplay..state.targetHighDisplay,
            lineComponent = ShapeComponent(color = shadeColor),
        )
    }

    val axisOverrider = remember(state.chartMinY, state.chartMaxY) {
        AxisValuesOverrider.fixed(
            minY = state.chartMinY.coerceAtLeast(0f),
            maxY = state.chartMaxY,
        )
    }

    val bottomFormatter = remember(state.rangeStartMillis) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val date = Instant.ofEpochMilli(state.rangeStartMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(value.toLong())
            Formatters.shortDate(date)
        }
    }

    val bottomItemPlacer = remember(state.range) {
        AxisItemPlacer.Horizontal.default(
            spacing = (state.range.days / 6).coerceAtLeast(1),
        )
    }

    Chart(
        chart = lineChart(
            lines = listOf(lineSpec(lineColor = accent)),
            decorations = listOf(thresholdLine),
            axisValuesOverrider = axisOverrider,
        ),
        chartModelProducer = viewModel.chartModelProducer,
        startAxis = rememberStartAxis(
            itemPlacer = remember { AxisItemPlacer.Vertical.default(maxItemCount = 5) },
        ),
        bottomAxis = rememberBottomAxis(
            valueFormatter = bottomFormatter,
            itemPlacer = bottomItemPlacer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )

    Spacer(Modifier.height(8.dp))
    Text(
        "Shaded band = your target range",
        style = MaterialTheme.typography.labelMedium,
        color = labelColor,
    )
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
                text = "Export report",
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
                title = ExportFormat.PDF.displayName,
                enabled = !isExporting,
                onClick = { onSelect(ExportFormat.PDF) },
            )
            ExportOptionRow(
                title = ExportFormat.CSV.displayName,
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
