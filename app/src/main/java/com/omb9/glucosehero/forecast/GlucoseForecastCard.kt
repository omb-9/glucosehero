package com.omb9.glucosehero.forecast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.ui.cgm.GlucoseFreshnessLabel
import com.omb9.glucosehero.ui.cgm.glucoseFreshnessSentence
import com.omb9.glucosehero.ui.components.WhyThisNumberToggle
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.WhyThisNumberCopy

/**
 * Compact 30/60-minute projection card for the Log and Stats screens.
 *
 * `snapshot.insufficientData` is "not enough points to model." [freshness]
 * is independently "the newest `glucose_readings` row is missing or old,"
 * so a 40-minute-old stream can still show numbers plus a stale caption.
 *
 * Contribution copy is rendered from [ForecastDisplayFormatter], never
 * reverse-engineered from the projected points.
 */
@Composable
fun GlucoseForecastCard(
    snapshot: GlucoseForecastSnapshot?,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    freshness: GlucoseFreshness? = null,
    use24HourTime: Boolean = false,
    onOpenDosingProfile: () -> Unit = {},
) {
    if (snapshot == null) return
    val at30 = snapshot.at30Min
    val at60 = snapshot.at60Min
    val explanation = ForecastDisplayFormatter.format(snapshot, unit, use24HourTime)
    var whyExpanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.forecast_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val icon = when {
                    snapshot.velocityMgdlPerMin > 0.15 -> Icons.Filled.TrendingUp
                    snapshot.velocityMgdlPerMin < -0.15 -> Icons.Filled.TrendingDown
                    else -> Icons.Filled.TrendingFlat
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            val freshnessCaption = glucoseFreshnessSentence(freshness)
            val label30 = stringResource(R.string.forecast_30_min_label)
            val label60 = stringResource(R.string.forecast_60_min_label)
            GlucoseFreshnessLabel(freshness = freshness)
            if (freshness is GlucoseFreshness.Stale || freshness is GlucoseFreshness.NoData) {
                Spacer(Modifier.height(8.dp))
            }
            if (!snapshot.insufficientData && !snapshot.dosingProfileInvalid &&
                at30 != null && at60 != null
            ) {
                val value30 = Formatters.glucoseWithUnit(at30.glucoseMgdl, unit)
                val value60 = Formatters.glucoseWithUnit(at60.glucoseMgdl, unit)
                val valuesDescription = if (freshnessCaption == null) {
                    stringResource(
                        R.string.forecast_values_a11y,
                        label30,
                        value30,
                        label60,
                        value60,
                    )
                } else {
                    stringResource(
                        R.string.forecast_values_a11y_with_freshness,
                        freshnessCaption,
                        label30,
                        value30,
                        label60,
                        value60,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = valuesDescription
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ForecastValue(
                        label = label30,
                        value = value30,
                    )
                    ForecastValue(
                        label = label60,
                        value = value60,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.forecast_iob_cob,
                        "%.1f".format(snapshot.iobUnits),
                        snapshot.cobGrams.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (snapshot.dosingProfileInvalid) {
                Text(
                    text = stringResource(R.string.forecast_dosing_profile_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (freshness !is GlucoseFreshness.NoData) {
                Text(
                    text = stringResource(R.string.forecast_insufficient_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WhyThisNumberToggle(
                expanded = whyExpanded,
                onToggle = { whyExpanded = !whyExpanded },
            )
            AnimatedVisibility(visible = whyExpanded) {
                ForecastWhyThisNumberBody(
                    explanation = explanation,
                    onOpenDosingProfile = onOpenDosingProfile,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.forecast_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForecastWhyThisNumberBody(
    explanation: ForecastDisplayExplanation,
    onOpenDosingProfile: () -> Unit,
) {
    val spoken = forecastWhySpokenSentence(explanation)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            if (explanation.dosingProfileInvalid) {
                Text(
                    text = stringResource(R.string.forecast_dosing_profile_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (explanation.insufficientData) {
                Text(
                    text = stringResource(R.string.forecast_insufficient_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.forecast_why_start,
                        explanation.startGlucose,
                        explanation.startTime,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.forecast_why_trend,
                        explanation.trendContribution,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.forecast_why_insulin,
                        explanation.iobUnits,
                        explanation.insulinDrop,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.forecast_why_carbs,
                        explanation.cobGrams,
                        explanation.carbRise,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                explanation.clamp30?.let { bound ->
                    Text(
                        text = stringResource(R.string.forecast_why_clamp_30, bound),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                explanation.clamp60?.let { bound ->
                    Text(
                        text = stringResource(R.string.forecast_why_clamp_60, bound),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ForecastSegmentCopy(explanation)
            }
            Text(
                text = stringResource(R.string.forecast_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (explanation.dosingProfileInvalid) {
            TextButton(onClick = onOpenDosingProfile) {
                Text(stringResource(R.string.bolus_open_dosing_profile))
            }
        }
    }
}

@Composable
private fun ForecastSegmentCopy(explanation: ForecastDisplayExplanation) {
    when {
        explanation.horizonCrossedSegmentBoundary && explanation.segments.isNotEmpty() -> {
            val items = ArrayList<String>(explanation.segments.size)
            for (seg in explanation.segments) {
                items += stringResource(
                    R.string.forecast_why_segment_item,
                    "${seg.startText}-${seg.endText}",
                    seg.isfWithUnit,
                    seg.cirText,
                )
            }
            Text(
                text = stringResource(R.string.forecast_why_segments, items.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        explanation.claimsSingleIsf && explanation.segments.size == 1 -> {
            val seg = explanation.segments.first()
            Text(
                text = stringResource(
                    R.string.forecast_why_segment_single,
                    seg.isfWithUnit,
                    seg.startText,
                    seg.endText,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun forecastWhySpokenSentence(
    explanation: ForecastDisplayExplanation,
): String {
    val clauses = buildList {
        if (explanation.dosingProfileInvalid) {
            add(stringResource(R.string.forecast_dosing_profile_invalid))
        } else if (explanation.insufficientData) {
            add(stringResource(R.string.forecast_insufficient_data))
        } else {
            add(
                stringResource(
                    R.string.forecast_why_start,
                    explanation.startGlucose,
                    explanation.startTime,
                ),
            )
            add(
                stringResource(R.string.forecast_why_trend, explanation.trendContribution),
            )
            add(
                stringResource(
                    R.string.forecast_why_insulin,
                    explanation.iobUnits,
                    explanation.insulinDrop,
                ),
            )
            add(
                stringResource(
                    R.string.forecast_why_carbs,
                    explanation.cobGrams,
                    explanation.carbRise,
                ),
            )
            explanation.clamp30?.let {
                add(stringResource(R.string.forecast_why_clamp_30, it))
            }
            explanation.clamp60?.let {
                add(stringResource(R.string.forecast_why_clamp_60, it))
            }
            if (explanation.horizonCrossedSegmentBoundary && explanation.segments.isNotEmpty()) {
                val items = ArrayList<String>(explanation.segments.size)
                for (seg in explanation.segments) {
                    items += stringResource(
                        R.string.forecast_why_segment_item,
                        "${seg.startText}-${seg.endText}",
                        seg.isfWithUnit,
                        seg.cirText,
                    )
                }
                add(stringResource(R.string.forecast_why_segments, items.joinToString(", ")))
            } else if (explanation.claimsSingleIsf && explanation.segments.size == 1) {
                val seg = explanation.segments.first()
                add(
                    stringResource(
                        R.string.forecast_why_segment_single,
                        seg.isfWithUnit,
                        seg.startText,
                        seg.endText,
                    ),
                )
            }
        }
        add(stringResource(R.string.forecast_disclaimer))
    }
    return WhyThisNumberCopy.spokenSentence(clauses)
}

@Composable
private fun ForecastValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
