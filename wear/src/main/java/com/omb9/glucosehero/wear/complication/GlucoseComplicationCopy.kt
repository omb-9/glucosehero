package com.omb9.glucosehero.wear.complication

import com.omb9.glucosehero.wear.data.GlucoseFreshness
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot

/**
 * Pure complication copy so tests can cover Fresh / Stale / NoData without
 * a Wear service. Long text always carries a short age: UPDATE_PERIOD is
 * advisory and a watch face can keep a payload on screen past the 8-minute
 * Fresh ceiling, so a bare "112" is not safe.
 *
 * FEATURE: cgm-direct-ingest
 */
internal object GlucoseComplicationCopy {

    fun longText(
        hasReading: Boolean,
        valueLine: String,
        compactAge: String,
    ): String {
        if (!hasReading) return compactAge
        return "$valueLine · $compactAge"
    }

    fun contentDescription(
        label: String,
        snapshot: WearGlucoseSnapshot,
        compactAge: String,
    ): String {
        if (!snapshot.hasReading) return compactAge
        return "$label ${snapshot.displayValue} ${snapshot.unitLabel} ${snapshot.trend.arrow} $compactAge"
    }

    fun title(
        freshness: GlucoseFreshness,
        compactAge: String,
        trendArrow: String,
        fallbackLabel: String,
    ): String {
        if (freshness is GlucoseFreshness.Stale || freshness is GlucoseFreshness.NoData) {
            return compactAge
        }
        return trendArrow.ifBlank { fallbackLabel }
    }
}
