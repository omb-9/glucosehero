package com.omb9.glucosehero.forecast

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.Formatters

/**
 * Compact 30/60-minute projection card for the Log and Stats screens.
 */
@Composable
fun GlucoseForecastCard(
    snapshot: GlucoseForecastSnapshot?,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null) return
    val at30 = snapshot.at30Min
    val at60 = snapshot.at60Min
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
            if (snapshot.insufficientData || at30 == null || at60 == null) {
                Text(
                    text = stringResource(R.string.forecast_insufficient_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ForecastValue(
                        label = stringResource(R.string.forecast_30_min_label),
                        value = Formatters.glucoseWithUnit(at30.glucoseMgdl, unit),
                    )
                    ForecastValue(
                        label = stringResource(R.string.forecast_60_min_label),
                        value = Formatters.glucoseWithUnit(at60.glucoseMgdl, unit),
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
