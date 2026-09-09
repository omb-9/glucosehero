package com.omb9.glucosehero.util

/**
 * Chart-series budgets for CGM-scale data.
 *
 * SQLite buckets cap how many rows ever leave Room. LTTB then cuts that series
 * to a memory budget in the ViewModel and again to a pixel budget in Compose
 * so a 24h window stays a few hundred points while longer windows keep a
 * slightly denser line (the closest thing this screen has to zoom).
 */
object ChartDownsample {

    const val FIVE_MIN_MILLIS = 5L * 60L * 1000L
    const val FIFTEEN_MIN_MILLIS = 15L * 60L * 1000L
    const val ONE_HOUR_MILLIS = 60L * 60L * 1000L

    const val MIN_PIXEL_POINTS = 120

    /**
     * Null means "do not pre-bucket": a 24h CGM stream is already 288–1440
     * points and LTTB can consume it directly. Longer windows use 5 min,
     * 15 min, or 1 hour averages so 30/90 day queries never materialize
     * ~40k–130k raw samples.
     */
    fun bucketMillisForRangeDays(rangeDays: Int): Long? = when {
        rangeDays <= 1 -> null
        rangeDays <= 7 -> FIVE_MIN_MILLIS
        rangeDays <= 30 -> FIFTEEN_MIN_MILLIS
        else -> ONE_HOUR_MILLIS
    }

    /** Upper bound for the stats chart series after LTTB. */
    fun memoryThreshold(rangeDays: Int): Int = when {
        rangeDays <= 1 -> 400
        rangeDays <= 7 -> 560
        rangeDays <= 14 -> 640
        rangeDays <= 30 -> 720
        else -> 800
    }

    /**
     * Target point count from the chart's laid-out width. Shorter ranges use a
     * slightly higher density so a 24h "zoom" still looks like a continuous
     * line without feeding Vico a point per pixel.
     */
    fun pixelThreshold(widthDp: Float, rangeDays: Int): Int {
        val densityBoost = if (rangeDays <= 1) 1.25f else 1.0f
        val fromWidth = if (widthDp.isFinite() && widthDp > 0f) {
            (widthDp * densityBoost).toInt()
        } else {
            memoryThreshold(rangeDays)
        }
        return fromWidth.coerceIn(MIN_PIXEL_POINTS, memoryThreshold(rangeDays))
    }
}
