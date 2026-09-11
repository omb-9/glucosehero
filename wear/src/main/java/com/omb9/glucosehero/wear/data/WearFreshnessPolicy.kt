package com.omb9.glucosehero.wear.data

/**
 * Elapsed-time policy for the complication and tile.
 *
 * `UPDATE_PERIOD_SECONDS` is advisory: Wear OS commonly will not auto-request
 * a complication more than once per ~30 minutes even when the manifest asks
 * for [COMPLICATION_UPDATE_PERIOD_SECONDS] (10 minutes, rounded up from the
 * 8-minute Fresh ceiling). The Fresh-to-Stale flip is therefore driven by
 * [WearFreshnessAlarmReceiver] plus `ComplicationData.setValidTimeRange`, not
 * by the hint alone.
 *
 * Tile [tileFreshnessIntervalMillis] is similarly a hint. While Fresh, the
 * tile timeline carries a second entry that becomes valid at the Stale
 * boundary so the watch can flip the caption without a process wake.
 *
 * FEATURE: cgm-direct-ingest
 */
internal object WearFreshnessPolicy {

    const val COMPLICATION_UPDATE_PERIOD_SECONDS = 600

    fun visualSignature(snapshot: WearGlucoseSnapshot, nowMillis: Long): String {
        val freshness = snapshot.freshness(nowMillis)
        return listOf(
            snapshot.hasReading.toString(),
            snapshot.glucoseMgdl.toString(),
            snapshot.timestampMillis.toString(),
            snapshot.trend.wireName,
            snapshot.unit,
            snapshot.lastQuickEntry,
            freshnessKey(freshness),
        ).joinToString("|")
    }

    fun shouldRequestUpdate(previousSignature: String?, nextSignature: String): Boolean =
        previousSignature != nextSignature

    fun freshnessKey(freshness: GlucoseFreshness): String = when (freshness) {
        GlucoseFreshness.Fresh -> "fresh"
        is GlucoseFreshness.Stale -> {
            val parts = formatGlucoseAge(freshness.ageMillis)
            "stale:${parts.quantity}:${parts.unit}"
        }
        GlucoseFreshness.NoData -> "nodata"
    }

    /**
     * Alarm / valid-time instant for the Fresh-to-Stale crossing. Null when
     * already Stale or NoData so we do not wake every minute to rewrite
     * "9 min ago" as "10 min ago".
     */
    fun staleCrossingAtMillis(timestampMillis: Long, nowMillis: Long): Long? {
        val freshness = GlucoseFreshness.classify(timestampMillis, nowMillis)
        if (freshness !is GlucoseFreshness.Fresh) return null
        val triggerAt = timestampMillis + GlucoseFreshness.FRESH_MAX_AGE_MILLIS + 1L
        return triggerAt.takeIf { it > nowMillis }
    }

    fun complicationValidUntilMillis(
        snapshot: WearGlucoseSnapshot,
        nowMillis: Long,
    ): Long? {
        if (!snapshot.hasReading) return null
        return staleCrossingAtMillis(snapshot.timestampMillis, nowMillis)
    }

    fun tileFreshnessIntervalMillis(freshness: GlucoseFreshness): Long = when (freshness) {
        GlucoseFreshness.Fresh -> GlucoseFreshness.FRESH_MAX_AGE_MILLIS
        is GlucoseFreshness.Stale -> when (formatGlucoseAge(freshness.ageMillis).unit) {
            GlucoseAgeUnit.MINUTES -> 15L * 60L * 1000L
            GlucoseAgeUnit.HOURS -> 60L * 60L * 1000L
            GlucoseAgeUnit.DAYS -> 6L * 60L * 60L * 1000L
        }
        GlucoseFreshness.NoData -> 60L * 60L * 1000L
    }
}

