package com.omb9.glucosehero.ui.glance

import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.data.cgm.formatGlucoseAge

/**
 * Pure helpers for the home-screen widget's elapsed-time refresh.
 *
 * Glance only redraws when [WidgetRefresher.refresh] runs. Ingest already
 * does that on new rows. These helpers drive a second path: a one-shot at
 * the Fresh-to-Stale boundary, plus a skip when the caption would not
 * change (so the 15-minute [com.omb9.glucosehero.work.ForecastRefreshWorker]
 * does not rewrite an unchanged RemoteViews).
 *
 * Why not rely on `updatePeriodMillis`? The App Widget host floors that at
 * 30 minutes, which is later than [GlucoseFreshness.FRESH_MAX_AGE_MILLIS].
 * WorkManager's periodic floor is 15 minutes; that is the backstop for
 * hour/day buckets. The safety-critical Fresh-to-Stale flip is a one-shot
 * timed to the 8-minute ceiling. Doze can delay that one-shot; the 15-minute
 * job still runs. No foreground service.
 *
 * FEATURE: cgm-direct-ingest
 */
internal object WidgetFreshnessPolicy {

    /**
     * Stable key for the glucose number plus freshness caption. Independent
     * of localized strings so tests and skip-logic do not need a Context.
     */
    fun renderKey(
        glucoseMgdl: Double?,
        timestampMillis: Long?,
        nowMillis: Long,
        unitName: String,
    ): String {
        val freshness = GlucoseFreshness.classify(timestampMillis, nowMillis)
        return listOf(
            glucoseMgdl?.toString().orEmpty(),
            timestampMillis?.toString().orEmpty(),
            unitName,
            freshnessKey(freshness),
        ).joinToString("|")
    }

    fun shouldUpdate(previousKey: String?, nextKey: String): Boolean =
        previousKey != nextKey

    /**
     * Delay until the compact caption would leave Fresh. Null when already
     * Stale/NoData: minute-by-minute stale captions are not worth a wake;
     * [com.omb9.glucosehero.work.ForecastRefreshWorker] covers those buckets.
     */
    fun millisUntilStaleCaption(
        timestampMillis: Long?,
        nowMillis: Long,
    ): Long? {
        if (timestampMillis == null || timestampMillis <= 0L) return null
        val freshness = GlucoseFreshness.classify(timestampMillis, nowMillis)
        if (freshness !is GlucoseFreshness.Fresh) return null
        val ageMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
        return GlucoseFreshness.FRESH_MAX_AGE_MILLIS - ageMillis + 1L
    }

    fun freshnessKey(freshness: GlucoseFreshness): String = when (freshness) {
        GlucoseFreshness.Fresh -> "fresh"
        is GlucoseFreshness.Stale -> {
            val parts = formatGlucoseAge(freshness.ageMillis)
            "stale:${parts.quantity}:${parts.unit}"
        }
        GlucoseFreshness.NoData -> "nodata"
    }
}
