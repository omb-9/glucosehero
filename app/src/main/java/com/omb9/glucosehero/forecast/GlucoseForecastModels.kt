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
 * On-device 30-60 minute glucose projection snapshot persisted for UI.
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
) {
    fun pointAt(minutesAhead: Int): GlucoseForecastPoint? =
        points.minByOrNull { kotlin.math.abs(it.minutesAhead - minutesAhead) }

    val at30Min: GlucoseForecastPoint? get() = pointAt(30)
    val at60Min: GlucoseForecastPoint? get() = pointAt(60)
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
) {
    data class GlucoseSample(
        val timestampMillis: Long,
        val glucoseMgdl: Double,
    )
}
