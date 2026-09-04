package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable

/**
 * Insulin-dosing parameters backing the Smart Bolus and IOB calculators.
 *
 * All glucose values are canonical mg/dL; [diaHours] is the duration of
 * insulin action in hours, [cirRatio] the carb-to-insulin ratio (grams of
 * carbohydrate covered by one unit of insulin), and [isfMgdl] the insulin
 * sensitivity factor (mg/dL drop per insulin unit).
 */
@Immutable
data class BolusSettings(
    val diaHours: Float = 4.0f,
    val cirRatio: Float = 10.0f,
    val isfMgdl: Float = 50.0f,
    val targetGlucoseMgdl: Float = 100.0f,
)