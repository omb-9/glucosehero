package com.omb9.glucosehero.util

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.DosingProfileIssue
import com.omb9.glucosehero.domain.model.DosingResolveResult
import com.omb9.glucosehero.domain.model.DosingSegment
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Smart-bolus outcome for UI. Dosing paths must not invent a number when the
 * stored profile is invalid.
 *
 * [Ready] carries the calculator breakdown and the segment
 * [recommendBolus] actually resolved, so the UI can name that block without
 * calling [com.omb9.glucosehero.domain.model.DosingProfile.at] again.
 */
sealed class BolusRecommendation {
    @Immutable
    data class Ready(
        val breakdown: BolusRecommendationBreakdown,
        val segment: DosingSegment,
        val segmentEnd: LocalTime,
    ) : BolusRecommendation() {
        val units: Double get() = breakdown.units
    }

    data class Refused(val issues: List<DosingProfileIssue>) : BolusRecommendation()
    data object Incomplete : BolusRecommendation()
}

/**
 * Resolves [load] at [now] and, when valid, calls [BolusCalculator.recommend]
 * with those scalars. Never falls back to default ISF/CIR/target.
 */
fun recommendBolus(
    load: DosingProfileLoad,
    now: Instant,
    zoneId: ZoneId,
    currentGlucoseMgdl: Double,
    carbsGrams: Double,
    insulinOnBoard: Double,
): BolusRecommendation {
    when (load) {
        is DosingProfileLoad.Invalid ->
            return BolusRecommendation.Refused(load.issues)
        is DosingProfileLoad.Valid -> {
            val resolved = load.profile.resolvedBolusSettings(now, zoneId)
            return when (resolved) {
                is DosingResolveResult.Refused -> BolusRecommendation.Refused(resolved.issues)
                is DosingResolveResult.Ok -> BolusRecommendation.Ready(
                    breakdown = BolusCalculator.recommend(
                        currentGlucoseMgdl = currentGlucoseMgdl,
                        targetGlucoseMgdl = resolved.settings.targetGlucoseMgdl.toDouble(),
                        carbsGrams = carbsGrams,
                        carbRatio = resolved.settings.cirRatio.toDouble(),
                        insulinSensitivityMgdl = resolved.settings.isfMgdl.toDouble(),
                        insulinOnBoard = insulinOnBoard,
                    ),
                    segment = resolved.segment,
                    segmentEnd = load.profile.nextSegmentStart(resolved.segment),
                )
            }
        }
    }
}
