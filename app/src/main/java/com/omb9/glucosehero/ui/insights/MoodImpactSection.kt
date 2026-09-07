package com.omb9.glucosehero.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import kotlin.math.abs

/**
 * One sentence disclosing that the mood↔glucose correlation is bidirectional
 * and the app cannot determine which way it runs. Deliberately short and
 * observational, with no interpretation or advice.
 */
private const val MOOD_CONFOUNDER =
    "The app cannot tell which way this relationship runs — a mood may follow glucose, or glucose may follow a mood."

/**
 * The separate Mood section: same row/card styling as the food list, but with
 * no carbs or bolus figures (mood rows carry none) and a single confounder
 * sentence in place of the food list's insulin disclosure.
 */
@Composable
fun MoodImpactSection(
    moods: List<TagImpactUi>,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (moods.isEmpty()) return
    val maxAbsDelta = remember(moods) {
        moods.maxOfOrNull { abs(it.medianDeltaMgdl) } ?: 0.0
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Mood",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) {
                    Text("See all")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = MOOD_CONFOUNDER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            moods.forEach { mood ->
                MoodImpactCard(item = mood, maxAbsDelta = maxAbsDelta)
            }
        }
    }
}

/**
 * A mood row: label (prefix already stripped), occurrence count with the same
 * provisional band, median 2h delta, p25–p75 spread, and the diverging delta
 * bar. Deliberately omits carbs/bolus, which mood rows do not carry.
 */
@Composable
fun MoodImpactCard(
    item: TagImpactUi,
    maxAbsDelta: Double,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.tag,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

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

@Preview(showBackground = true)
@Composable
private fun MoodImpactSectionPreview() {
    GlucoseHeroTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoodImpactSection(
                moods = listOf(
                    TagImpactUi(
                        tag = "Anxious",
                        occurrences = 6,
                        medianDeltaMgdl = 42.0,
                        p25DeltaMgdl = 10.0,
                        p75DeltaMgdl = 70.0,
                        avgCarbsGrams = null,
                        avgBolusUnits = null,
                        unit = GlucoseUnit.MGDL,
                    ),
                    TagImpactUi(
                        tag = "Tired",
                        occurrences = 3,
                        medianDeltaMgdl = -15.0,
                        p25DeltaMgdl = -30.0,
                        p75DeltaMgdl = -4.0,
                        avgCarbsGrams = null,
                        avgBolusUnits = null,
                        unit = GlucoseUnit.MGDL,
                    ),
                ),
            )
        }
    }
}
