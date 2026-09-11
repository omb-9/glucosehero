package com.omb9.glucosehero.data.cgm

import androidx.compose.runtime.Immutable

/**
 * First-class UI state for "how old is the newest glucose point?"
 *
 * Why this exists: the on-device forecast can still emit a 30/60-minute
 * projection (or `insufficientData`) from a lookback that no longer includes
 * a live CGM stream. A number computed from a 40-minute-old sample must not
 * look like a current reading. `insufficientData` stays "not enough points
 * to model"; this type is only "the newest row in `glucose_readings` is
 * missing or old."
 *
 * Source of truth is the `glucose_readings` view (CGM samples union
 * user-authored fingersticks), not a single `glucose_samples` source. Callers
 * pass that newest timestamp in; this type does not query Room.
 *
 * Thresholds (5-minute CGM cadence):
 * - **Fresh** if the newest sample is at most [FRESH_MAX_AGE_MILLIS] (8 minutes)
 *   old: one missed 5-minute interval plus ~3 minutes for ingest delay and
 *   clock skew.
 * - **Stale** after that, carrying the age so UI can say "40 min ago".
 * - **NoData** when there is no usable row (null or non-positive timestamp).
 *
 * A future timestamp is treated as age 0 (Fresh), not as invalid.
 *
 * Wear keeps a copy at `wear/.../data/GlucoseFreshness.kt` because `:wear`
 * cannot depend on `:app`. [FRESH_MAX_AGE_MILLIS] must stay equal on both
 * sides; `GlucoseFreshnessLockstepTest` fails if they diverge.
 *
 * FEATURE: cgm-direct-ingest
 */
@Immutable
sealed class GlucoseFreshness {
    /** Newest sample is within one CGM interval plus slack. */
    @Immutable
    data object Fresh : GlucoseFreshness()

    /**
     * Newest sample is older than [FRESH_MAX_AGE_MILLIS].
     *
     * @param ageMillis milliseconds since that sample, never negative.
     */
    @Immutable
    data class Stale(val ageMillis: Long) : GlucoseFreshness()

    /** No row in `glucose_readings` (or a non-positive timestamp). */
    @Immutable
    data object NoData : GlucoseFreshness()

    companion object {
        /**
         * Inclusive Fresh ceiling: 8 minutes.
         *
         * Common CGM transmitters report every 5 minutes. 8 minutes is one
         * missed interval plus slack so a reading that arrived at 5:07 is
         * still current, while a 40-minute gap is not.
         */
        const val FRESH_MAX_AGE_MILLIS: Long = 8L * 60L * 1000L

        /**
         * Classifies [newestTimestampMillis] against [nowMillis].
         *
         * @param newestTimestampMillis epoch millis of the newest
         *   `glucose_readings` row, or null when the view is empty.
         */
        fun classify(
            newestTimestampMillis: Long?,
            nowMillis: Long,
        ): GlucoseFreshness {
            if (newestTimestampMillis == null || newestTimestampMillis <= 0L) {
                return NoData
            }
            val ageMillis = (nowMillis - newestTimestampMillis).coerceAtLeast(0L)
            return if (ageMillis <= FRESH_MAX_AGE_MILLIS) {
                Fresh
            } else {
                Stale(ageMillis)
            }
        }
    }
}

/** Display bucket for a stale (or compact-age) duration. */
enum class GlucoseAgeUnit {
    MINUTES,
    HOURS,
    DAYS,
}

/**
 * Whole-unit age for string resources (`%1$d min ago`).
 *
 * Minutes until 60, then whole hours until 24, then whole days. Sub-minute
 * ages format as 1 minute so a just-crossed Stale boundary never prints 0.
 */
data class GlucoseAgeParts(
    val quantity: Long,
    val unit: GlucoseAgeUnit,
)

/**
 * Buckets [ageMillis] into a quantity plus [GlucoseAgeUnit] for UI copy.
 *
 * Pure function so widget, Wear, and Compose can share the same rounding
 * without Android resources.
 */
fun formatGlucoseAge(ageMillis: Long): GlucoseAgeParts {
    val minutes = (ageMillis.coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 60L -> GlucoseAgeParts(minutes.coerceAtLeast(1L), GlucoseAgeUnit.MINUTES)
        minutes < 24L * 60L -> GlucoseAgeParts(minutes / 60L, GlucoseAgeUnit.HOURS)
        else -> GlucoseAgeParts((minutes / (24L * 60L)).coerceAtLeast(1L), GlucoseAgeUnit.DAYS)
    }
}
