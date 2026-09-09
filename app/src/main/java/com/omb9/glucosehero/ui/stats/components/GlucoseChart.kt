package com.omb9.glucosehero.ui.stats.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.ui.stats.GlucoseChartPoint
import com.omb9.glucosehero.ui.stats.GlucoseMarker
import com.omb9.glucosehero.ui.stats.MarkerCategory
import com.omb9.glucosehero.util.ChartDownsample
import com.omb9.glucosehero.util.ChartRevealTier
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.GlucoseRangeColor
import com.omb9.glucosehero.util.Lttb
import com.omb9.glucosehero.util.RangeCategory
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private val MarkerPointSize = 8.dp

/**
 * How many dp one x-unit (one day) spans. The 24h window uses a larger spacing so a single day
 * still fills the viewport; longer ranges keep the original 32dp/day and scroll horizontally.
 */
private val DayPointSpacing = 32.dp
private val SingleDayPointSpacing = 288.dp

private val ChartHeight = 220.dp
private val ChartLayerPadding = 16.dp
private val CaptionSpacing = 8.dp

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val RangeBandAlpha = 0.14f
private const val VerticalAxisTickCount = 5

/** Identifies a foreground marker series so taps can be mapped back to an entry id. */
private data class MarkerSeriesKey(val entryId: Long)

@Immutable
private data class ChartLineGeometry(
    val xs: List<Double>,
    val ys: List<Float>,
    val mean: Float?,
    val inRangeShare: Float?,
)

private val HighTriangleShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private val LowTriangleShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

/**
 * The glucose trend line chart, with interactive foreground markers layered on top.
 *
 * The background line is the continuous trend sourced from the `glucose_readings` view (CGM
 * samples plus manual entries), already bucketed and LTTB-reduced before it reaches composition.
 * Pixel-width LTTB runs again here, keyed on data + range (the zoom proxy), so Vico never sees a
 * raw CGM stream. The foreground layer is a set of sparse, tappable markers sourced only from
 * user-authored `entries`. Marker detail is gated by the selected range (24h/7d = value labels,
 * 14d = dots, 30d/90d = line only), and both the background range bands and markers use the
 * fixed five-band range palette.
 */
@Composable
fun GlucoseChart(
    points: List<GlucoseChartPoint>,
    markers: List<GlucoseMarker>,
    enabledCategories: Set<MarkerCategory>,
    targetLow: Float,
    targetHigh: Float,
    veryLowThreshold: Float,
    veryHighThreshold: Float,
    minY: Float,
    maxY: Float,
    rangeDays: Int,
    rangeStartMillis: Long,
    themeMode: ThemeMode,
    use24Hour: Boolean,
    onMarkerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val pixelThreshold = remember(maxWidth, rangeDays) {
            ChartDownsample.pixelThreshold(maxWidth.value, rangeDays)
        }
        val sampledPoints = remember(points, pixelThreshold, rangeDays) {
            Lttb.downsample(points, pixelThreshold, { it.x }, { it.y.toDouble() })
        }
        val lineGeometry by remember(sampledPoints, targetLow, targetHigh) {
            derivedStateOf {
                ChartLineGeometry(
                    xs = sampledPoints.map { it.x },
                    ys = sampledPoints.map { it.y },
                    mean = chartMean(sampledPoints),
                    inRangeShare = chartInRangeShare(sampledPoints, targetLow, targetHigh),
                )
            }
        }

        GlucoseChartPlot(
            lineGeometry = lineGeometry,
            markers = markers,
            enabledCategories = enabledCategories,
            targetLow = targetLow,
            targetHigh = targetHigh,
            veryLowThreshold = veryLowThreshold,
            veryHighThreshold = veryHighThreshold,
            minY = minY,
            maxY = maxY,
            rangeDays = rangeDays,
            rangeStartMillis = rangeStartMillis,
            themeMode = themeMode,
            use24Hour = use24Hour,
            onMarkerClick = onMarkerClick,
        )
    }
}

@Composable
private fun GlucoseChartPlot(
    lineGeometry: ChartLineGeometry,
    markers: List<GlucoseMarker>,
    enabledCategories: Set<MarkerCategory>,
    targetLow: Float,
    targetHigh: Float,
    veryLowThreshold: Float,
    veryHighThreshold: Float,
    minY: Float,
    maxY: Float,
    rangeDays: Int,
    rangeStartMillis: Long,
    themeMode: ThemeMode,
    use24Hour: Boolean,
    onMarkerClick: (Long) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val modelProducer = remember { CartesianChartModelProducer() }

    val scrollState = rememberVicoScrollState(scrollEnabled = true)

    val tier = ChartRevealTier.forRangeDays(rangeDays)
    val linePointSpacing = if (rangeDays <= 1) SingleDayPointSpacing else DayPointSpacing
    val markerPointSpacing = linePointSpacing - MarkerPointSize

    val effectiveThemeMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) ThemeMode.AMOLED else ThemeMode.LIGHT
        else -> themeMode
    }

    val rangeProvider = remember(minY, maxY) {
        CartesianLayerRangeProvider.fixed(minY = minY.toDouble(), maxY = maxY.toDouble())
    }

    val markersByCategory = remember(markers) { markers.groupBy { it.category } }

    val visibleCategories = remember(tier, markersByCategory, enabledCategories) {
        if (tier == ChartRevealTier.LINE_ONLY) {
            emptyList()
        } else {
            MarkerCategory.entries.filter { category ->
                (category in enabledCategories) && !markersByCategory[category].isNullOrEmpty()
            }
        }
    }

    LaunchedEffect(lineGeometry.xs, lineGeometry.ys, lineGeometry.mean, lineGeometry.inRangeShare, markersByCategory, visibleCategories) {
        modelProducer.runTransaction {
            lineModel {
                series(lineGeometry.xs, lineGeometry.ys)
            }
            visibleCategories.forEach { category ->
                val categoryMarkers = markersByCategory.getValue(category)
                lineModel {
                    categoryMarkers.forEach { marker ->
                        series(
                            listOf(marker.x),
                            listOf(marker.y),
                            key = MarkerSeriesKey(marker.entryId),
                        )
                    }
                }
            }
        }
    }

    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(accent)),
                stroke = LineCartesianLayer.LineStroke.Continuous(
                    thickness = 2.dp,
                    cap = StrokeCap.Round,
                ),
                areaFill = null,
                interpolator = LineCartesianLayer.Interpolator.cubic(),
            ),
        ),
        pointSpacing = linePointSpacing,
        rangeProvider = rangeProvider,
    )

    val valueLabelComponent = rememberTextComponent(
        style = TextStyle(fontSize = 10.sp, color = labelColor),
    )

    val foregroundLayers = remember(
        markersByCategory,
        visibleCategories,
        tier,
        effectiveThemeMode,
        rangeProvider,
        valueLabelComponent,
        markerPointSpacing,
    ) {
        visibleCategories.map { category ->
            val categoryMarkers = markersByCategory.getValue(category)
            val lines = categoryMarkers.map { marker ->
                buildMarkerLine(
                    marker = marker,
                    tier = tier,
                    themeMode = effectiveThemeMode,
                    labelComponent = valueLabelComponent,
                )
            }
            LineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                pointSpacing = markerPointSpacing,
                rangeProvider = rangeProvider,
            )
        }
    }

    val allLayers = remember(lineLayer, foregroundLayers) {
        listOf<CartesianLayer<*>>(lineLayer) + foregroundLayers
    }

    val isHourAxis = rangeDays <= 1
    val bottomFormatter = remember(rangeStartMillis, isHourAxis, use24Hour) {
        if (isHourAxis) {
            CartesianValueFormatter { _, value, _ ->
                val timestamp = rangeStartMillis + (value * MILLIS_PER_DAY).toLong()
                Formatters.hourOfDay(timestamp, use24Hour)
            }
        } else {
            CartesianValueFormatter { _, value, _ ->
                val date = Instant.ofEpochMilli(rangeStartMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .plusDays(value.toLong())
                Formatters.shortDate(date)
            }
        }
    }

    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = bottomFormatter,
        itemPlacer = remember(rangeDays) {
            HorizontalAxis.ItemPlacer.aligned(
                spacing = { (rangeDays / 6).coerceAtLeast(1) },
            )
        },
    )

    val decorations = remember(
        minY,
        maxY,
        targetLow,
        targetHigh,
        veryLowThreshold,
        veryHighThreshold,
        effectiveThemeMode,
    ) {
        buildRangeDecorations(
            minY = minY,
            maxY = maxY,
            targetLow = targetLow,
            targetHigh = targetHigh,
            veryLowThreshold = veryLowThreshold,
            veryHighThreshold = veryHighThreshold,
            themeMode = effectiveThemeMode,
        )
    }

    val invisibleMarker = remember { object : CartesianMarker {} }

    val markerController = remember(onMarkerClick) {
        object : CartesianMarkerController {
            override fun shouldAcceptInteraction(
                interaction: Interaction,
                targets: List<CartesianMarker.Target>,
            ): Boolean {
                if (interaction is Interaction.Tap) {
                    val entryId = targets
                        .filterIsInstance<LineCartesianLayerMarkerTarget>()
                        .flatMap { it.points }
                        .map { it.entry.seriesKey }
                        .filterIsInstance<MarkerSeriesKey>()
                        .firstOrNull()
                        ?.entryId
                    if (entryId != null) {
                        onMarkerClick(entryId)
                        return true
                    }
                }
                return false
            }

            override fun shouldShowMarker(
                interaction: Interaction,
                targets: List<CartesianMarker.Target>,
            ): Boolean = false
        }
    }

    val chart = rememberCartesianChart(
        *allLayers.toTypedArray(),
        startAxis = VerticalAxis.rememberStart(
            itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { VerticalAxisTickCount }) },
        ),
        bottomAxis = bottomAxis,
        decorations = decorations,
        marker = invisibleMarker,
        markerController = markerController,
        getXStep = { _, _, _ -> if (isHourAxis) 0.25 else 1.0 },
        layerPadding = { CartesianLayerPadding(scalableStart = ChartLayerPadding, scalableEnd = ChartLayerPadding) },
    )

    Column {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .graphicsLayer(),
            scrollState = scrollState,
        )

        Spacer(Modifier.height(CaptionSpacing))
        Text(
            "Tap a marker to open it",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun chartMean(points: List<GlucoseChartPoint>): Float? {
    if (points.isEmpty()) return null
    var sum = 0.0
    for (point in points) sum += point.y
    return (sum / points.size).toFloat()
}

private fun chartInRangeShare(
    points: List<GlucoseChartPoint>,
    targetLow: Float,
    targetHigh: Float,
): Float? {
    if (points.isEmpty()) return null
    var inRange = 0
    for (point in points) {
        if (point.y in targetLow..targetHigh) inRange++
    }
    return inRange.toFloat() / points.size
}

private fun buildRangeDecorations(
    minY: Float,
    maxY: Float,
    targetLow: Float,
    targetHigh: Float,
    veryLowThreshold: Float,
    veryHighThreshold: Float,
    themeMode: ThemeMode,
): List<HorizontalBox> = buildList {
    fun band(category: RangeCategory, from: Double, to: Double) {
        val clampedFrom = from.coerceAtLeast(minY.toDouble())
        val clampedTo = to.coerceAtMost(maxY.toDouble())
        if (clampedTo > clampedFrom) {
            add(
                HorizontalBox(
                    y = { clampedFrom..clampedTo },
                    box = ShapeComponent(
                        fill = Fill(
                            GlucoseRangeColor.colorFor(category, themeMode)
                                .copy(alpha = RangeBandAlpha),
                        ),
                    ),
                ),
            )
        }
    }

    band(RangeCategory.VERY_LOW, minY.toDouble(), veryLowThreshold.toDouble())
    band(RangeCategory.LOW, veryLowThreshold.toDouble(), targetLow.toDouble())
    band(RangeCategory.IN_RANGE, targetLow.toDouble(), targetHigh.toDouble())
    band(RangeCategory.HIGH, targetHigh.toDouble(), veryHighThreshold.toDouble())
    band(RangeCategory.VERY_HIGH, veryHighThreshold.toDouble(), maxY.toDouble())
}

private fun buildMarkerLine(
    marker: GlucoseMarker,
    tier: ChartRevealTier,
    themeMode: ThemeMode,
    labelComponent: TextComponent,
): LineCartesianLayer.Line {
    val rangeColor = GlucoseRangeColor.colorFor(marker.range, themeMode)
    val categoryColor = markerCategoryColor(marker.category)
    val shape = when (marker.range) {
        RangeCategory.IN_RANGE -> CircleShape
        RangeCategory.HIGH, RangeCategory.VERY_HIGH -> HighTriangleShape
        RangeCategory.LOW, RangeCategory.VERY_LOW -> LowTriangleShape
    }

    val point = LineCartesianLayer.Point(
        component = ShapeComponent(
            fill = Fill(rangeColor),
            shape = shape,
            strokeFill = Fill(categoryColor),
            strokeThickness = 2.dp,
        ),
        size = MarkerPointSize,
    )

    val pointProvider = object : LineCartesianLayer.PointProvider {
        override fun getPoint(
            entry: LineCartesianLayerModel.Entry,
            extraStore: ExtraStore,
        ): LineCartesianLayer.Point = point

        override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = point
    }

    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill.Transparent),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
        areaFill = null,
        pointProvider = pointProvider,
        interpolator = LineCartesianLayer.Interpolator.Sharp,
        dataLabel = if (tier >= ChartRevealTier.DOT_PLUS_VALUE) labelComponent else null,
        dataLabelPosition = Position.Vertical.Top,
        dataLabelValueFormatter = { _, value, _ ->
            formatDisplayValue(value)
        },
    )
}

/**
 * Fixed category palette, chosen to be visually distinct from all six `AccentColor` values (and
 * from the range palette). This is a categorical signal, not the user's brand accent.
 */
private fun markerCategoryColor(category: MarkerCategory): Color = when (category) {
    MarkerCategory.MEAL -> Color(0xFF00897B)
    MarkerCategory.EXERCISE -> Color(0xFF5D4037)
    MarkerCategory.NOTE -> Color(0xFF303F9F)
}

private fun formatDisplayValue(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(rounded)
}
