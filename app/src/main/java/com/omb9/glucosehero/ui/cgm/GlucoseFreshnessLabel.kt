package com.omb9.glucosehero.ui.cgm

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.GlucoseAgeParts
import com.omb9.glucosehero.data.cgm.GlucoseAgeUnit
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.data.cgm.formatGlucoseAge

/**
 * Resolves [GlucoseFreshness] to `freshness_*` string resources.
 *
 * Sentence copy is for the forecast card and stats header. Compact copy is
 * for the Glance widget. Fresh sentence copy is omitted: a current stream
 * should not add a banner.
 */
internal data class FreshnessStringRef(
    @StringRes val id: Int,
    val quantity: Long? = null,
)

internal fun GlucoseFreshness.sentenceRef(): FreshnessStringRef? = when (this) {
    GlucoseFreshness.Fresh -> null
    is GlucoseFreshness.Stale -> staleRef(
        formatGlucoseAge(ageMillis),
        sentence = true,
    )
    GlucoseFreshness.NoData -> FreshnessStringRef(R.string.freshness_no_data)
}

internal fun GlucoseFreshness.compactRef(): FreshnessStringRef = when (this) {
    GlucoseFreshness.Fresh -> FreshnessStringRef(R.string.freshness_compact_fresh)
    is GlucoseFreshness.Stale -> staleRef(
        formatGlucoseAge(ageMillis),
        sentence = false,
    )
    GlucoseFreshness.NoData -> FreshnessStringRef(R.string.freshness_compact_no_data)
}

private fun staleRef(
    age: GlucoseAgeParts,
    sentence: Boolean,
): FreshnessStringRef {
    val id = when (age.unit) {
        GlucoseAgeUnit.MINUTES -> if (sentence) {
            R.string.freshness_stale_minutes
        } else {
            R.string.freshness_compact_stale_minutes
        }
        GlucoseAgeUnit.HOURS -> if (sentence) {
            R.string.freshness_stale_hours
        } else {
            R.string.freshness_compact_stale_hours
        }
        GlucoseAgeUnit.DAYS -> if (sentence) {
            R.string.freshness_stale_days
        } else {
            R.string.freshness_compact_stale_days
        }
    }
    return FreshnessStringRef(id, age.quantity)
}

@Composable
private fun FreshnessStringRef.resolve(): String {
    val qty = quantity
    return if (qty == null) stringResource(id) else stringResource(id, qty)
}

private fun FreshnessStringRef.resolve(context: Context): String {
    val qty = quantity
    return if (qty == null) context.getString(id) else context.getString(id, qty)
}

/** Sentence label, or null when Fresh / unknown so callers can skip the row. */
@Composable
fun glucoseFreshnessSentence(freshness: GlucoseFreshness?): String? =
    freshness?.sentenceRef()?.resolve()

/** Compact label for widgets; always a string once [freshness] is known. */
fun GlucoseFreshness.compactText(context: Context): String = compactRef().resolve(context)

/**
 * Caption under a derived glucose number. Hidden while Fresh or until the
 * first repository emission ([freshness] null).
 *
 * This Text is its own TalkBack node so the age is not dropped when a
 * neighbouring number is focused. Callers that set `mergeDescendants` on a
 * parent must put this caption into that parent's contentDescription
 * (same standard as the Wear complication). liveRegion announces the
 * caption when a screen left open flips Fresh to Stale.
 */
@Composable
fun GlucoseFreshnessLabel(
    freshness: GlucoseFreshness?,
    modifier: Modifier = Modifier,
) {
    val text = glucoseFreshnessSentence(freshness) ?: return
    Text(
        text = text,
        modifier = modifier.semantics {
            contentDescription = text
            liveRegion = LiveRegionMode.Polite
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
