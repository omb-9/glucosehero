package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable

/**
 * Insulin-dosing parameters backing IOB decay and glucose forecast math.
 *
 * This is the **resolved, point-in-time** value type. Time-of-day ISF, CIR,
 * and target live on [DosingProfile] and resolve *to* [BolusSettings] so
 * IOB and the forecast stay a pure function of scalars. [diaHours] is global
 * (not time-varying). The app stores and displays these values; it does not
 * compute an insulin dose from them.
 *
 * All glucose values are canonical mg/dL; [diaHours] is the duration of
 * insulin action in hours, [cirRatio] the carb-to-insulin ratio (grams of
 * carbohydrate covered by one unit of insulin), and [isfMgdl] the insulin
 * sensitivity factor (mg/dL drop per insulin unit).
 *
 * FEATURE: dosing-profiles
 */
@Immutable
data class BolusSettings(
    val diaHours: Float = 4.0f,
    val cirRatio: Float = 10.0f,
    val isfMgdl: Float = 50.0f,
    val targetGlucoseMgdl: Float = 100.0f,
)