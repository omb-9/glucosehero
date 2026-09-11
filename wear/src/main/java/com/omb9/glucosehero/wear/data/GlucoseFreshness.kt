package com.omb9.glucosehero.wear.data

/**
 * Watch-side copy of `app/src/main/java/com/omb9/glucosehero/data/cgm/GlucoseFreshness.kt`.
 *
 * Keep thresholds and [formatGlucoseAge] in lockstep with the phone. Wear
 * has no Room; it classifies the DataItem timestamp the phone already
 * sourced from `glucose_readings`. [FRESH_MAX_AGE_MILLIS] must stay equal
 * to the app module constant; `GlucoseFreshnessLockstepTest` (app) fails
 * if the two expressions diverge.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed class GlucoseFreshness {
    data object Fresh : GlucoseFreshness()
    data class Stale(val ageMillis: Long) : GlucoseFreshness()
    data object NoData : GlucoseFreshness()

    companion object {
        /** Inclusive Fresh ceiling: 8 minutes. One 5-minute CGM interval plus slack. */
        const val FRESH_MAX_AGE_MILLIS: Long = 8L * 60L * 1000L

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

enum class GlucoseAgeUnit {
    MINUTES,
    HOURS,
    DAYS,
}

data class GlucoseAgeParts(
    val quantity: Long,
    val unit: GlucoseAgeUnit,
)

fun formatGlucoseAge(ageMillis: Long): GlucoseAgeParts {
    val minutes = (ageMillis.coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 60L -> GlucoseAgeParts(minutes.coerceAtLeast(1L), GlucoseAgeUnit.MINUTES)
        minutes < 24L * 60L -> GlucoseAgeParts(minutes / 60L, GlucoseAgeUnit.HOURS)
        else -> GlucoseAgeParts((minutes / (24L * 60L)).coerceAtLeast(1L), GlucoseAgeUnit.DAYS)
    }
}
