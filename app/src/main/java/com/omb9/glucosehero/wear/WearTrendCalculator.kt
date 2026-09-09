package com.omb9.glucosehero.wear

import com.omb9.glucosehero.domain.model.GlucosePointRow

/**
 * Dexcom-style rate-of-change arrows from the phone glucose store. Uses the
 * first and last points inside a 20-minute window (CGM samples plus manual
 * readings from `glucose_readings`). Thresholds are mg/dL per minute:
 * 3 / 2 / 1 for double, single, and forty-five up or down.
 */
object WearTrendCalculator {
    const val WINDOW_MS: Long = 20L * 60L * 1000L
    const val MIN_SPAN_MS: Long = 2L * 60L * 1000L

    fun from(
        points: List<GlucosePointRow>,
        nowMillis: Long = System.currentTimeMillis(),
    ): WearTrend {
        val recent = points
            .filter { point ->
                point.glucoseMgdl.isFinite() &&
                    nowMillis - point.timestamp in 0..WINDOW_MS
            }
            .sortedBy { it.timestamp }
        if (recent.size < 2) return WearTrend.UNKNOWN
        val first = recent.first()
        val last = recent.last()
        val spanMs = last.timestamp - first.timestamp
        if (spanMs < MIN_SPAN_MS) return WearTrend.UNKNOWN
        val rate = (last.glucoseMgdl - first.glucoseMgdl) / (spanMs / 60_000.0)
        return when {
            rate >= 3.0 -> WearTrend.DOUBLE_UP
            rate >= 2.0 -> WearTrend.SINGLE_UP
            rate >= 1.0 -> WearTrend.FORTY_FIVE_UP
            rate > -1.0 -> WearTrend.FLAT
            rate > -2.0 -> WearTrend.FORTY_FIVE_DOWN
            rate > -3.0 -> WearTrend.SINGLE_DOWN
            else -> WearTrend.DOUBLE_DOWN
        }
    }
}
