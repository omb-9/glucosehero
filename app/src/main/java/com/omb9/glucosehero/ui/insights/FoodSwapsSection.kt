package com.omb9.glucosehero.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.omb9.glucosehero.util.FoodSwap
import com.omb9.glucosehero.util.Formatters
import kotlin.math.abs

@Composable
fun FoodSwapsSection(
    swaps: List<FoodSwap>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.food_swaps_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.food_swaps_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (swaps.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = stringResource(R.string.food_swaps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                swaps.forEach { swap ->
                    FoodSwapCard(swap = swap)
                }
            }
        }
    }
}

@Composable
private fun FoodSwapCard(swap: FoodSwap) {
    val fromDelta = Formatters.signedGlucoseWithUnit(swap.fromMedianDeltaMgdl, swap.unit)
    val toDelta = Formatters.signedGlucoseWithUnit(swap.toMedianDeltaMgdl, swap.unit)
    val improvement = Formatters.glucoseWithUnit(abs(swap.improvementMgdl), swap.unit)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = swap.fromTag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.food_swaps_arrow_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = swap.toTag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.food_swaps_comparison,
                    swap.fromTag,
                    fromDelta,
                    swap.fromOccurrences,
                    swap.toTag,
                    toDelta,
                    swap.toOccurrences,
                    improvement,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = swapReason(swap),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun swapReason(swap: FoodSwap): String {
    val carbs = when {
        swap.fromAvgCarbsGrams != null && swap.toAvgCarbsGrams != null ->
            stringResource(
                R.string.food_swaps_reason_carbs,
                Formatters.carbs(swap.fromAvgCarbsGrams),
                Formatters.carbs(swap.toAvgCarbsGrams),
            )
        else -> stringResource(R.string.food_swaps_reason_carbs_unknown)
    }
    val buffer = if (
        swap.toBufferScore != null &&
        swap.fromBufferScore != null &&
        swap.toBufferScore > swap.fromBufferScore
    ) {
        stringResource(R.string.food_swaps_reason_buffer)
    } else {
        ""
    }
    return listOf(carbs, buffer).filter { it.isNotBlank() }.joinToString(" ")
}
