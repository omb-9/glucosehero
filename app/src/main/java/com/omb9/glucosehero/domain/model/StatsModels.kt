package com.omb9.glucosehero.domain.model

enum class TimeRange(val days: Int, val label: String) {
    DAYS_7(7, "7D"),
    DAYS_14(14, "14D"),
    DAYS_30(30, "30D"),
    DAYS_90(90, "90D"),
}

/** Lightweight chart projection: only the columns the trend chart actually renders. */
data class GlucosePointRow(
    val timestamp: Long,
    val glucoseMgdl: Double,
)

/** Row shape returned by the GROUP BY day aggregate query. */
data class DailyGlucoseSummary(
    val day: String,
    val avgMgdl: Double,
    val minMgdl: Double,
    val maxMgdl: Double,
    val readings: Int,
)

/**
 * Aggregate glucose data for the rolling 90-day window used by the estimated
 * A1c card. [avgMgdl] is always canonical mg/dL regardless of display unit,
 * [readingCount] is the total number of glucose readings, and [loggedDays] is
 * the number of distinct local calendar days with at least one reading.
 */
data class GlucoseStats(
    val avgMgdl: Double?,
    val readingCount: Int,
    val loggedDays: Int,
)

/** Confidence bucket for the estimated A1c card. */
enum class Ea1cConfidence {
    INSUFFICIENT_DATA,
    BUILDING_ESTIMATE,
    FULL_90_DAY_WINDOW,
}
