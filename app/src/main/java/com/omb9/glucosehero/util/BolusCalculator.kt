package com.omb9.glucosehero.util

import androidx.compose.runtime.Immutable

/**
 * Pure, side-effect-free Smart Bolus recommendation math.
 *
 * Required Insulin = (Carbs / CIR) + ((Current Glucose - Target Glucose) / ISF) - IOB
 *
 * where CIR is the carb-to-insulin ratio (g per unit), ISF is the insulin
 * sensitivity factor (mg/dL per unit), and IOB is current insulin on board.
 * The result is floored at zero so the recommendation is never negative.
 *
 * Canonical glucose is mg/dL. Convert only at the display edge.
 *
 * The returned [BolusRecommendationBreakdown] is the single source of truth
 * for both the number and its explanation. UI must render these terms and
 * must not recompute meal, correction, or IOB arithmetic.
 */
object BolusCalculator {

    fun recommend(
        currentGlucoseMgdl: Double,
        targetGlucoseMgdl: Double,
        carbsGrams: Double,
        carbRatio: Double,
        insulinSensitivityMgdl: Double,
        insulinOnBoard: Double,
    ): BolusRecommendationBreakdown {
        val mealDose = carbsGrams / carbRatio
        val correctionDose = (currentGlucoseMgdl - targetGlucoseMgdl) / insulinSensitivityMgdl
        val rawTotal = mealDose + correctionDose - insulinOnBoard
        val units = rawTotal.coerceAtLeast(0.0)
        return BolusRecommendationBreakdown(
            currentGlucoseMgdl = currentGlucoseMgdl,
            targetGlucoseMgdl = targetGlucoseMgdl,
            carbsGrams = carbsGrams,
            carbRatio = carbRatio,
            insulinSensitivityMgdl = insulinSensitivityMgdl,
            insulinOnBoard = insulinOnBoard,
            mealDose = mealDose,
            correctionDose = correctionDose,
            rawTotal = rawTotal,
            units = units,
            zeroFloorApplied = rawTotal < 0.0,
        )
    }
}

/**
 * Intermediate Smart Bolus terms produced by [BolusCalculator.recommend].
 *
 * [units] is the value that used to be the bare `Double` return: the same
 * unrounded math, then [Double.coerceAtLeast] 0. When [zeroFloorApplied] is
 * true, [rawTotal] is negative and does not equal [units]; the UI must say so
 * rather than printing a sum that contradicts the headline.
 */
@Immutable
data class BolusRecommendationBreakdown(
    val currentGlucoseMgdl: Double,
    val targetGlucoseMgdl: Double,
    val carbsGrams: Double,
    val carbRatio: Double,
    val insulinSensitivityMgdl: Double,
    val insulinOnBoard: Double,
    val mealDose: Double,
    val correctionDose: Double,
    val rawTotal: Double,
    val units: Double,
    val zeroFloorApplied: Boolean,
)
