package com.omb9.glucosehero.forecast

import kotlinx.serialization.Serializable

/**
 * One projected glucose sample. All glucose values are canonical mg/dL.
 */
@Serializable
data class GlucoseForecastPoint(
    val timestampMillis: Long,
    val minutesAhead: Int,
    val glucoseMgdl: Double,
)

/**
 * ISF/CIR actually applied on some 5-minute step of the horizon.
 * [startHhmm] is local wall-clock `"HH:mm"`.
 */
@Serializable
data class ForecastDosingSegmentUsed(
    val startHhmm: String,
    val isfMgdl: Double,
    val cirRatio: Double,
)

/**
 * On-device 30-60 minute glucose projection snapshot persisted for UI.
 *
 * Contribution fields are the engine's own decomposition over the 30- and
 * 60-minute highlights, not values the UI should reverse-engineer from
 * [points]. New fields default so DataStore JSON written before this feature
 * still decodes.
 */
@Serializable
data class GlucoseForecastSnapshot(
    val generatedAtMillis: Long,
    val currentMgdl: Double,
    val currentTimestampMillis: Long,
    val velocityMgdlPerMin: Double,
    val iobUnits: Double,
    val cobGrams: Double,
    val points: List<GlucoseForecastPoint>,
    val sampleCount: Int,
    val insufficientData: Boolean = false,
    /** True when the stored dosing profile is invalid; UI must not show numbers as a recommendation. FEATURE: dosing-profiles */
    val dosingProfileInvalid: Boolean = false,
    val trendEffectMgdl30: Double = 0.0,
    val insulinEffectMgdl30: Double = 0.0,
    val carbEffectMgdl30: Double = 0.0,
    val unclampedMgdl30: Double = 0.0,
    val clampMin30: Boolean = false,
    val clampMax30: Boolean = false,
    val trendEffectMgdl60: Double = 0.0,
    val insulinEffectMgdl60: Double = 0.0,
    val carbEffectMgdl60: Double = 0.0,
    val unclampedMgdl60: Double = 0.0,
    val clampMin60: Boolean = false,
    val clampMax60: Boolean = false,
    val horizonCrossedSegmentBoundary: Boolean = false,
    val dosingSegmentsUsed: List<ForecastDosingSegmentUsed> = emptyList(),
) {
    fun pointAt(minutesAhead: Int): GlucoseForecastPoint? =
        points.minByOrNull { kotlin.math.abs(it.minutesAhead - minutesAhead) }

    val at30Min: GlucoseForecastPoint? get() = pointAt(30)
    val at60Min: GlucoseForecastPoint? get() = pointAt(60)

    /** True when a single ISF was used for every step of the horizon. */
    val claimsSingleIsf: Boolean
        get() = !horizonCrossedSegmentBoundary && dosingSegmentsUsed.size <= 1
}

/**
 * Inputs for [GlucoseForecastEngine]. Glucose and insulin math use mg/dL and
 * insulin units; carbs are grams.
 */
data class GlucoseForecastInput(
    val samples: List<GlucoseSample>,
    val boluses: List<com.omb9.glucosehero.util.IobCalculator.BolusEntry>,
    val meals: List<com.omb9.glucosehero.util.CarbAbsorptionCalculator.CarbEntry>,
    val diaHours: Double,
    val cirRatio: Double,
    val isfMgdl: Double,
    val carbActionHours: Double = com.omb9.glucosehero.util.CarbAbsorptionCalculator.DEFAULT_ACTION_HOURS,
    /**
     * When non-null, ISF/CIR at each horizon step come from this profile.
     * Null keeps the historical scalar path so existing tests stay bit-identical.
     * FEATURE: dosing-profiles
     */
    val dosingProfile: com.omb9.glucosehero.domain.model.DosingProfile? = null,
    val zoneId: java.time.ZoneId? = null,
) {
    data class GlucoseSample(
        val timestampMillis: Long,
        val glucoseMgdl: Double,
    )
}
