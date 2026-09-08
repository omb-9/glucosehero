package com.omb9.glucosehero.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import com.omb9.glucosehero.util.TagImpactCopy
import kotlin.math.abs

/**
 * Palette for a tag-impact card. Live screens use [tagImpactCardColors]; share
 * snapshots use [tagImpactShareColors] so the bitmap stays true-black even when
 * the user is in Light theme.
 */
@Immutable
data class TagImpactCardColors(
    val container: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
)

@Composable
fun tagImpactCardColors(
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    outline: Color = MaterialTheme.colorScheme.outlineVariant,
    onSurface: Color = MaterialTheme.colorScheme.onSurface,
    onSurfaceVariant: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Color = MaterialTheme.colorScheme.primary,
): TagImpactCardColors = TagImpactCardColors(
    container = container,
    outline = outline,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    accent = accent,
)

/** Share-image identity: true black, white type, gray stabilizers, orange accent. */
fun tagImpactShareColors(): TagImpactCardColors = TagImpactCardColors(
    container = Color(0xFF000000),
    outline = Color(0xFF3A3A3E),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF9E9EA4),
    accent = Color(0xFFFF7043),
)

/**
 * A single food-impact row: tag, occurrence count (with provisional language),
 * median 2h delta, the p25–p75 spread, and the average carbs and bolus that
 * accompany it. Phrasing stays observational, never causal.
 *
 * Pass [onShare] to show the share action used for bitmap export. Share
 * snapshots should pass [onShare] = null and [tagImpactShareColors] so action
 * chrome and theme colors never land on the image.
 */
@Composable
fun TagImpactCard(
    item: TagImpactUi,
    maxAbsDelta: Double,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    colors: TagImpactCardColors = tagImpactCardColors(),
) {
    val accent = colors.accent
    val onSurface = colors.onSurface
    val onSurfaceVariant = colors.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onShare != null) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share ${item.tag}",
                            tint = onSurfaceVariant,
                        )
                    }
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Hide ${item.tag}",
                            tint = onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = occurrenceLabel(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceVariant,
                )
                if (item.isProvisional) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = accent.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "Provisional · ${item.occurrences} of $MIN_CONFIDENT_OCCURRENCES",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (item.isProvisional) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = TagImpactCopy.provisionalProgress(item.occurrences),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = directionGlyph(item),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = deltaSentence(item),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Middle 50%: ${spreadLabel(item)}",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = observationSentence(item),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = confounderLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            DeltaBar(
                delta = item.medianDeltaMgdl,
                maxAbs = maxAbsDelta,
                accent = accent,
                trackColor = accent.copy(alpha = 0.14f),
                zeroLineColor = onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
        }
    }
}

internal fun occurrenceLabel(item: TagImpactUi): String =
    "${item.occurrences} ${if (item.occurrences == 1) "occurrence" else "occurrences"}"

internal fun directionGlyph(item: TagImpactUi): String = when {
    item.medianDeltaMgdl > 0.0005 -> "↑"
    item.medianDeltaMgdl < -0.0005 -> "↓"
    else -> "→"
}

internal fun deltaSentence(item: TagImpactUi, whenPhrase: String = "two hours later"): String =
    TagImpactCopy.deltaHeadline(item.medianDeltaMgdl, item.unit, whenPhrase)

internal fun observationSentence(item: TagImpactUi, whenPhrase: String = "two hours later"): String =
    TagImpactCopy.observation(
        tag = item.tag,
        medianDeltaMgdl = item.medianDeltaMgdl,
        occurrences = item.occurrences,
        avgBolusUnits = item.avgBolusUnits,
        unit = item.unit,
        whenPhrase = whenPhrase,
    )

internal fun spreadLabel(item: TagImpactUi): String =
    TagImpactCopy.spread(item.p25DeltaMgdl, item.p75DeltaMgdl, item.unit)

internal fun confounderLabel(item: TagImpactUi): String =
    TagImpactCopy.confounder(item.avgCarbsGrams, item.avgBolusUnits)

/**
 * A zero-centred horizontal diverging bar: positive deltas extend right of the
 * centre line, negative deltas extend left. Direction is carried by position
 * plus the arrow glyph/signed text on the card, never by hue alone — so the
 * single accent fill survives every accent in both Light and AMOLED.
 */
@Composable
internal fun DeltaBar(
    delta: Double,
    maxAbs: Double,
    accent: Color,
    trackColor: Color,
    zeroLineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = corner,
        )

        val centerX = size.width / 2f
        drawLine(
            color = zeroLineColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = 1.dp.toPx(),
        )

        val fraction = if (maxAbs <= 0.0) 0f else (abs(delta) / maxAbs).toFloat().coerceIn(0f, 1f)
        if (fraction <= 0f) return@Canvas

        val halfWidth = (size.width / 2f) * fraction
        val left = if (delta >= 0.0) centerX else centerX - halfWidth
        val right = if (delta >= 0.0) centerX + halfWidth else centerX
        drawRoundRect(
            color = accent,
            topLeft = Offset(left, 0f),
            size = Size((right - left).coerceAtLeast(1f), size.height),
            cornerRadius = corner,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TagImpactCardPreview() {
    GlucoseHeroTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TagImpactCard(
                item = TagImpactUi(
                    tag = "#pizza",
                    kind = TagKind.HASHTAG,
                    occurrences = 11,
                    medianDeltaMgdl = 85.0,
                    p25DeltaMgdl = 40.0,
                    p75DeltaMgdl = 120.0,
                    avgCarbsGrams = 62.0,
                    avgBolusUnits = 4.2,
                    unit = GlucoseUnit.MGDL,
                ),
                maxAbsDelta = 85.0,
                onShare = {},
            )
            TagImpactCard(
                item = TagImpactUi(
                    tag = "oatmeal",
                    kind = TagKind.FOOD,
                    occurrences = 3,
                    medianDeltaMgdl = -12.0,
                    p25DeltaMgdl = -30.0,
                    p75DeltaMgdl = -2.0,
                    avgCarbsGrams = 41.0,
                    avgBolusUnits = 3.1,
                    unit = GlucoseUnit.MGDL,
                ),
                maxAbsDelta = 85.0,
            )
        }
    }
}
