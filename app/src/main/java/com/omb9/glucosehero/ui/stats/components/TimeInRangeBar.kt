package com.omb9.glucosehero.ui.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.util.GlucoseRangeColor
import com.omb9.glucosehero.util.RangeCategory
import kotlin.math.roundToInt

/** One slice of the time-in-range bar, already normalized to a percentage (0..100). */
data class TimeInRangeSegment(
    val category: RangeCategory,
    val percent: Float,
)

/**
 * A single horizontal stacked bar with five bands (very low → very high).
 *
 * Percentages are computed by the ViewModel over the `glucose_readings` view so CGM data is
 * included. The in-range percentage is shown as a prominent number, and each band is labeled with
 * its percentage only when the band is wide enough to fit it. A compact legend sits underneath so
 * color is never the only signal.
 */
@Composable
fun TimeInRangeBar(
    segments: List<TimeInRangeSegment>,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val inRangePercent = segments.firstOrNull { it.category == RangeCategory.IN_RANGE }?.percent

    Column(modifier = modifier) {
        if (inRangePercent != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${inRangePercent.roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "time in range",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Zero-percent segments are skipped: `RowScope.weight` throws on a weight of zero, and
            // an empty band should render nothing anyway.
            segments.filter { it.percent > 0f }.forEach { segment ->
                val color = GlucoseRangeColor.colorFor(segment.category, themeMode)
                val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .weight(segment.percent)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints {
                        val label = "${segment.percent.roundToInt()}%"
                        val availableWidthPx = with(density) { maxWidth.toPx() }
                        val labelWidthPx =
                            textMeasurer.measure(AnnotatedString(label), labelStyle).size.width
                        if (labelWidthPx <= availableWidthPx) {
                            Text(label, style = labelStyle, color = onColor, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            segments.forEach { segment ->
                val color = GlucoseRangeColor.colorFor(segment.category, themeMode)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = segment.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
