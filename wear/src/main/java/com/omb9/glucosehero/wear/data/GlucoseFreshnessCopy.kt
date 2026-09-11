package com.omb9.glucosehero.wear.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omb9.glucosehero.wear.R

/** Compact `freshness_*` copy for the complication, tile, and watch activity. */
fun GlucoseFreshness.compactText(context: Context): String = when (this) {
    GlucoseFreshness.Fresh -> context.getString(R.string.freshness_compact_fresh)
    is GlucoseFreshness.Stale -> {
        val age = formatGlucoseAge(ageMillis)
        val id = when (age.unit) {
            GlucoseAgeUnit.MINUTES -> R.string.freshness_compact_stale_minutes
            GlucoseAgeUnit.HOURS -> R.string.freshness_compact_stale_hours
            GlucoseAgeUnit.DAYS -> R.string.freshness_compact_stale_days
        }
        context.getString(id, age.quantity)
    }
    GlucoseFreshness.NoData -> context.getString(R.string.freshness_compact_no_data)
}

@Composable
fun glucoseFreshnessCompactLabel(
    freshness: GlucoseFreshness,
): String = when (freshness) {
    GlucoseFreshness.Fresh -> stringResource(R.string.freshness_compact_fresh)
    is GlucoseFreshness.Stale -> {
        val age = formatGlucoseAge(freshness.ageMillis)
        val id = when (age.unit) {
            GlucoseAgeUnit.MINUTES -> R.string.freshness_compact_stale_minutes
            GlucoseAgeUnit.HOURS -> R.string.freshness_compact_stale_hours
            GlucoseAgeUnit.DAYS -> R.string.freshness_compact_stale_days
        }
        stringResource(id, age.quantity)
    }
    GlucoseFreshness.NoData -> stringResource(R.string.freshness_compact_no_data)
}

fun WearGlucoseSnapshot.freshness(nowMillis: Long = System.currentTimeMillis()): GlucoseFreshness =
    GlucoseFreshness.classify(
        newestTimestampMillis = timestampMillis.takeIf { hasReading },
        nowMillis = nowMillis,
    )
