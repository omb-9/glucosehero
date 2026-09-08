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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.util.TagExtractor
import com.omb9.glucosehero.util.TagImpactCopy
import kotlin.math.abs

/**
 * The "Sleep & cycle" section: same card styling as food/mood, but each delta
 * is a window-vs-baseline measurement rather than a 2-hour post-event change.
 */
@Composable
fun LifestyleImpactSection(
    items: List<TagImpactUi>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val maxAbsDelta = remember(items) { items.maxOfOrNull { abs(it.medianDeltaMgdl) } ?: 0.0 }
    Column(modifier = modifier) {
        Text(
            text = "Sleep & cycle",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = WINDOW_CONFOUNDER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                LifestyleImpactCard(item = item, maxAbsDelta = maxAbsDelta)
            }
        }
    }
}

@Composable
fun LifestyleImpactCard(
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
                    text = windowDeltaSentence(item),
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
                text = observationSentence(item, whenPhrase = windowWhenPhrase(item.tag)),
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

private const val WINDOW_CONFOUNDER =
    "Sleep compares overnight glucose to the reading before sleep; " +
        "cycle compares glucose during a period to the week before it."

private fun windowWhenPhrase(tag: String): String =
    if (tag == TagExtractor.SLEEP_TAG) "overnight" else "during cycles"

private fun windowDeltaSentence(item: TagImpactUi): String =
    deltaSentence(item, whenPhrase = windowWhenPhrase(item.tag))